//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package net.sf.gogui.gogui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.sf.gogui.game.BoardUpdater;
import net.sf.gogui.game.ConstNode;
import net.sf.gogui.game.GameInformation;
import net.sf.gogui.game.GameTree;
import net.sf.gogui.game.MarkType;
import net.sf.gogui.game.Node;
import net.sf.gogui.game.NodeUtil;
import net.sf.gogui.game.TimeSettings;
import net.sf.gogui.go.Board;
import net.sf.gogui.go.BoardUtil;
import net.sf.gogui.go.CountScore;
import net.sf.gogui.go.GoColor;
import net.sf.gogui.go.GoPoint;
import net.sf.gogui.go.Move;
import net.sf.gogui.gtp.GtpClient;
import net.sf.gogui.gtp.GtpError;
import net.sf.gogui.gtp.GtpSynchronizer;
import net.sf.gogui.gtp.GtpUtil;
import net.sf.gogui.gui.AnalyzeCommand;
import net.sf.gogui.gui.AnalyzeDialog;
import net.sf.gogui.gui.AnalyzeShow;
import net.sf.gogui.gui.BoardSizeDialog;
import net.sf.gogui.gui.Clock;
import net.sf.gogui.gui.Bookmark;
import net.sf.gogui.gui.BookmarkDialog;
import net.sf.gogui.gui.Comment;
import net.sf.gogui.gui.ContextMenu;
import net.sf.gogui.gui.EditBookmarksDialog;
import net.sf.gogui.gui.FindDialog;
import net.sf.gogui.gui.GameInfo;
import net.sf.gogui.gui.GameInfoDialog;
import net.sf.gogui.gui.GameTreePanel;
import net.sf.gogui.gui.GameTreeViewer;
import net.sf.gogui.gui.GtpShell;
import net.sf.gogui.gui.GuiBoard;
import net.sf.gogui.gui.GuiBoardUtil;
import net.sf.gogui.gui.GuiGtpClient;
import net.sf.gogui.gui.GuiUtil;
import net.sf.gogui.gui.Help;
import net.sf.gogui.gui.LiveGfx;
import net.sf.gogui.gui.OptionalMessage;
import net.sf.gogui.gui.ParameterDialog;
import net.sf.gogui.gui.RecentFileMenu;
import net.sf.gogui.gui.SelectProgram;
import net.sf.gogui.gui.Session;
import net.sf.gogui.gui.ScoreDialog;
import net.sf.gogui.gui.SimpleDialogs;
import net.sf.gogui.gui.StatusBar;
import net.sf.gogui.gui.Util;
import net.sf.gogui.sgf.SgfReader;
import net.sf.gogui.sgf.SgfWriter;
import net.sf.gogui.tex.TexWriter;
import net.sf.gogui.thumbnail.ThumbnailCreator;
import net.sf.gogui.thumbnail.ThumbnailPlatform;
import net.sf.gogui.util.ErrorMessage;
import net.sf.gogui.util.FileUtil;
import net.sf.gogui.util.Platform;
import net.sf.gogui.util.ProgressShow;
import net.sf.gogui.version.Version;

/** Graphical user interface to a Go program. */
public class GoGui
    extends JFrame
    implements ActionListener, AnalyzeDialog.Callback, GuiBoard.Listener,
               GameTreeViewer.Listener, GtpShell.Callback
{
    public GoGui(String program, String file, int move, String time,
                 boolean verbose, boolean computerBlack,
                 boolean computerWhite, boolean auto, String gtpFile,
                 String gtpCommand, String initAnalyze)
        throws GtpError, ErrorMessage
    {
        m_boardSize = m_prefs.getInt("boardsize", GoPoint.DEFAULT_SIZE);
        m_beepAfterMove = m_prefs.getBoolean("beep-after-move", true);
        if (file == null)
            m_file = null;
        else
            m_file = new File(file);
        m_gtpFile = gtpFile;
        m_gtpCommand = gtpCommand;
        m_move = move;
        m_computerBlack = computerBlack;
        m_computerWhite = computerWhite;
        m_auto = auto;
        m_verbose = verbose;
        m_initAnalyze = initAnalyze;
        m_showInfoPanel = true;
        m_showToolbar = false;

        Container contentPane = getContentPane();        
        m_innerPanel = new JPanel(new BorderLayout());
        contentPane.add(m_innerPanel, BorderLayout.CENTER);
        m_toolBar = new GoGuiToolBar(this);

        m_infoPanel = new JPanel(new BorderLayout());
        m_clock = new Clock();
        m_gameInfo = new GameInfo(m_clock);
        m_gameInfo.setBorder(GuiUtil.createSmallEmptyBorder());
        m_infoPanel.add(m_gameInfo, BorderLayout.NORTH);

        m_board = new Board(m_boardSize);

        m_guiBoard = new GuiBoard(m_boardSize);
        m_guiBoard.setListener(this);
        m_statusBar = new StatusBar();
        m_innerPanel.add(m_statusBar, BorderLayout.SOUTH);

        Comment.Listener commentListener = new Comment.Listener()
            {
                public void changed()
                {
                    cbCommentChanged();
                }

                public void textSelected(String text)
                {
                    if (text == null)
                        text = "";
                    GoPoint list[] =
                        GtpUtil.parsePointString(text, m_boardSize);
                    GuiBoardUtil.showPointList(m_guiBoard, list);
                }
            };
        m_comment = new Comment(commentListener);
        boolean fontFixed = m_prefs.getBoolean("comment-font-fixed", false);
        m_comment.setFontFixed(fontFixed);
        m_infoPanel.add(m_comment, BorderLayout.CENTER);
        m_splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                     m_guiBoard, m_infoPanel);
        GuiUtil.removeKeyBinding(m_splitPane, "F8");
        m_splitPane.setResizeWeight(1);
        m_innerPanel.add(m_splitPane, BorderLayout.CENTER);
        WindowAdapter windowAdapter = new WindowAdapter()
            {
                public void windowClosing(WindowEvent event)
                {
                    close();
                }
            };
        addWindowListener(windowAdapter);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        GuiUtil.setGoIcon(this);
        RecentFileMenu.Callback recentCallback = new RecentFileMenu.Callback()
            {
                public void fileSelected(String label, File file)
                {
                    if (! checkSaveGame())
                        return;
                    loadFile(file, -1);
                    boardChangedBegin(false, true);
                }
            };
        RecentFileMenu.Callback recentGtp = new RecentFileMenu.Callback()
            {
                public void fileSelected(String label, File file)
                {
                    if (m_gtpShell == null)
                        return;
                    sendGtpFile(file);
                    m_menuBar.addRecentGtp(file);
                }
            };
        m_menuBar = new GoGuiMenuBar(this, recentCallback, recentGtp);
        m_menuBar.selectBoardSizeItem(m_boardSize);
        boolean onlySupported
            = m_prefs.getBoolean("analyze-only-supported-commands", true);
        m_menuBar.setAnalyzeOnlySupported(onlySupported);
        m_menuBar.setAnalyzeSort(m_prefs.getBoolean("analyze-sort", true));
        m_menuBar.setGameTreeLabels(m_prefs.getInt("gametree-labels",
                                                 GameTreePanel.LABEL_NUMBER));
        m_menuBar.setGameTreeSize(m_prefs.getInt("gametree-size",
                                                 GameTreePanel.SIZE_NORMAL));
        boolean showSubtreeSizes =
            m_prefs.getBoolean("gametree-show-subtree-sizes", false);
        m_menuBar.setShowSubtreeSizes(showSubtreeSizes);
        m_menuBar.setAutoNumber(m_prefs.getBoolean("gtpshell-autonumber",
                                                   false));
        // JComboBox has problems on the Mac, see section Bugs in
        // documentation
        boolean completion
            = ! m_prefs.getBoolean("gtpshell-disable-completions",
                                Platform.isMac());
        m_menuBar.setCommandCompletion(completion);
        m_menuBar.setCommentFontFixed(fontFixed);
        m_menuBar.setTimeStamp(m_prefs.getBoolean("gtpshell-timestamp",
                                                  false));
        m_menuBar.setBeepAfterMove(m_beepAfterMove);
        m_menuBar.setShowInfoPanel(m_showInfoPanel);
        m_menuBar.setShowToolbar(m_showToolbar);
        m_showLastMove = m_prefs.getBoolean("show-last-move", true);        
        m_menuBar.setShowLastMove(m_showLastMove);
        m_showVariations = m_prefs.getBoolean("show-variations", false);
        m_menuBar.setShowVariations(m_showVariations);
        boolean showCursor = m_prefs.getBoolean("show-cursor", false);
        m_menuBar.setShowCursor(showCursor);
        boolean showGrid = m_prefs.getBoolean("show-grid", true);
        m_menuBar.setShowGrid(showGrid);
        m_guiBoard.setShowCursor(showCursor);
        m_guiBoard.setShowGrid(showGrid);
        setJMenuBar(m_menuBar.getMenuBar());
        if (program == null)
            m_program = m_prefs.get("program", null);
        else
            m_program = program;
        if (m_program != null && m_program.trim().equals(""))
            m_program = null;
        if (m_program == null)
        {
            m_toolBar.setComputerEnabled(false);
            m_menuBar.setComputerEnabled(false);
        }
        m_menuBar.setNormalMode();
        m_guiBoard.requestFocusInWindow();
        if (time != null)
            m_timeSettings = TimeSettings.parse(time);
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    initialize();
                }
            };
        SwingUtilities.invokeLater(callback);
    }
    
    public void actionPerformed(ActionEvent event)
    {
        String command = event.getActionCommand();
        if (isCommandInProgress()
            && ! command.equals("about")
            && ! command.equals("beep-after-move")
            && ! command.equals("comment-font-fixed")
            && ! command.equals("computer-black")
            && ! command.equals("computer-both")
            && ! command.equals("computer-none")
            && ! command.equals("computer-white")
            && ! command.equals("detach-program")
            && ! command.equals("gtpshell-save")
            && ! command.equals("gtpshell-save-commands")
            && ! command.equals("command-completion")
            && ! command.equals("auto-number")
            && ! command.equals("timestamp")
            && ! command.equals("help")
            && ! command.equals("interrupt")
            && ! command.equals("show-grid")
            && ! command.equals("show-shell")
            && ! command.equals("show-toolbar")
            && ! command.equals("show-tree")
            && ! command.equals("show-info-panel")
            && ! command.equals("show-last-move")
            && ! command.equals("exit"))
            return;
        if (command.equals("about"))
            cbAbout();
        else if (command.equals("add-bookmark"))
            cbAddBookmark();
        else if (command.equals("edit-bookmarks"))
            cbEditBookmarks();
        else if (command.equals("analyze"))
            cbAnalyze();
        else if (command.equals("analyze-only-supported"))
            cbAnalyzeOnlySupported();
        else if (command.equals("analyze-sort"))
            cbAnalyzeSort();
        else if (command.equals("attach-program"))
            cbAttachProgram();
        else if (command.equals("auto-number"))
            cbAutoNumber();
        else if (command.equals("back-to-main-variation"))
            cbBackToMainVar();
        else if (command.equals("backward"))
            cbBackward(1);
        else if (command.equals("backward-10"))
            cbBackward(10);
        else if (command.equals("beep-after-move"))
            cbBeepAfterMove();
        else if (command.equals("beginning"))
            cbBeginning();
        else if (command.equals("board-size-other"))
            cbBoardSizeOther();
        else if (command.startsWith("board-size-"))
            cbBoardSize(command.substring("board-size-".length()));
        else if (command.startsWith("bookmark-"))
            cbBookmark(command.substring("bookmark-".length()));
        else if (command.equals("clock-halt"))
            cbClockHalt();
        else if (command.equals("clock-resume"))
            cbClockResume();
        else if (command.equals("clock-restore"))
            cbClockRestore();
        else if (command.equals("command-completion"))
            cbCommandCompletion();
        else if (command.equals("comment-font-fixed"))
            cbCommentFontFixed();
        else if (command.equals("computer-black"))
            computerBlack();
        else if (command.equals("computer-both"))
            cbComputerBoth();
        else if (command.equals("computer-none"))
            computerNone();
        else if (command.equals("computer-white"))
            computerWhite();
        else if (command.equals("detach-program"))
            cbDetachProgram();
        else if (command.equals("end"))
            cbEnd();
        else if (command.equals("exit"))
            close();
        else if (command.equals("export-ascii"))
            cbExportAscii();
        else if (command.equals("export-clipboard"))
            cbExportClipboard();
        else if (command.equals("export-latex"))
            cbExportLatex();
        else if (command.equals("export-latex-position"))
            cbExportLatexPosition();
        else if (command.equals("export-sgf-position"))
            cbExportSgfPosition();
        else if (command.equals("find-in-comments"))
            cbFindInComments();
        else if (command.equals("find-next"))
            cbFindNext();
        else if (command.equals("forward"))
            cbForward(1);
        else if (command.equals("forward-10"))
            cbForward(10);
        else if (command.equals("game-info"))
            cbGameInfo();
        else if (command.equals("goto"))
            cbGoto();
        else if (command.equals("goto-variation"))
            cbGotoVariation();
        else if (command.equals("gametree-move"))
            cbGameTreeLabels(GameTreePanel.LABEL_MOVE);
        else if (command.equals("gametree-number"))
            cbGameTreeLabels(GameTreePanel.LABEL_NUMBER);
        else if (command.equals("gametree-none"))
            cbGameTreeLabels(GameTreePanel.LABEL_NONE);
        else if (command.equals("gametree-large"))
            cbGameTreeSize(GameTreePanel.SIZE_LARGE);
        else if (command.equals("gametree-normal"))
            cbGameTreeSize(GameTreePanel.SIZE_NORMAL);
        else if (command.equals("gametree-small"))
            cbGameTreeSize(GameTreePanel.SIZE_SMALL);
        else if (command.equals("gametree-tiny"))
            cbGameTreeSize(GameTreePanel.SIZE_TINY);
        else if (command.equals("gametree-show-subtree-sizes"))
            cbGameTreeShowSubtreeSizes();
        else if (command.equals("gtpshell-save"))
            cbGtpShellSave();
        else if (command.equals("gtpshell-save-commands"))
            cbGtpShellSaveCommands();
        else if (command.equals("gtpshell-send-file"))
            cbGtpShellSendFile();
        else if (command.startsWith("handicap-"))
            cbHandicap(command.substring("handicap-".length()));
        else if (command.equals("help"))
            cbHelp();
        else if (command.equals("interrupt"))
            cbInterrupt();
        else if (command.equals("keep-only-main-variation"))
            cbKeepOnlyMainVariation();
        else if (command.equals("keep-only-position"))
            cbKeepOnlyPosition();
        else if (command.equals("make-main-variation"))
            cbMakeMainVariation();
        else if (command.equals("next-variation"))
            cbNextVariation();
        else if (command.equals("next-earlier-variation"))
            cbNextEarlierVariation();
        else if (command.equals("new-game"))
            cbNewGame(m_boardSize);
        else if (command.equals("open"))
            cbOpen();
        else if (command.equals("pass"))
            cbPass();
        else if (command.equals("play"))
            cbPlay(false);
        else if (command.equals("play-single"))
            cbPlay(true);
        else if (command.equals("previous-variation"))
            cbPreviousVariation();
        else if (command.equals("previous-earlier-variation"))
            cbPreviousEarlierVariation();
        else if (command.equals("print"))
            cbPrint();
        else if (command.equals("save"))
            cbSave();
        else if (command.equals("save-as"))
            cbSaveAs();
        else if (command.equals("score"))
            cbScore();
        else if (command.equals("score-cancel"))
            cbScoreDone(false);
        else if (command.equals("score-done"))
            cbScoreDone(true);
        else if (command.equals("setup"))
            cbSetup();
        else if (command.equals("setup-black"))
            cbSetupBlack();
        else if (command.equals("setup-white"))
            cbSetupWhite();
        else if (command.equals("show-cursor"))
            cbShowCursor();
        else if (command.equals("show-grid"))
            cbShowGrid();
        else if (command.equals("show-info-panel"))
            cbShowInfoPanel();
        else if (command.equals("show-last-move"))
            cbShowLastMove();
        else if (command.equals("show-shell"))
            cbShowShell();
        else if (command.equals("show-toolbar"))
            cbShowToolbar();
        else if (command.equals("show-tree"))
            cbShowTree();
        else if (command.equals("show-variations"))
            cbShowVariations();
        else if (command.equals("timestamp"))
            cbTimeStamp();
        else if (command.equals("truncate"))
            cbTruncate();
        else if (command.equals("truncate-children"))
            cbTruncateChildren();
        else
            assert(false);
    }
    
    public void cbAnalyze()
    {        
        if (m_gtp == null)
            return;
        if (! checkProgramInSync())
            return;
        if (m_menuBar.getShowAnalyze())
        {
            if (m_analyzeDialog == null)
            {
                boolean onlySupported = m_menuBar.getAnalyzeOnlySupported();
                boolean sort = m_menuBar.getAnalyzeSort();
                m_analyzeDialog =
                    new AnalyzeDialog(this, this, onlySupported, sort,
                                      m_gtp.getSupportedCommands(),
                                      m_programAnalyzeCommands,
                                      m_gtp);
                m_analyzeDialog.addWindowListener(new WindowAdapter()
                    {
                        public void windowClosing(WindowEvent e)
                        {
                            m_menuBar.setShowAnalyze(false);
                        }
                    });
                m_analyzeDialog.setBoardSize(m_board.getSize());
                restoreSize(m_analyzeDialog, "analyze");
                setTitle();
            }
            m_analyzeDialog.setVisible(true);
        }
        else
        {
            if (m_analyzeDialog != null)
                m_analyzeDialog.close();
            m_analyzeDialog = null;
        }
    }

    public void cbAnalyzeOnlySupported()
    {
        boolean onlySupported = m_menuBar.getAnalyzeOnlySupported();
        m_prefs.putBoolean("analyze-only-supported-commands", onlySupported);
        if (m_analyzeDialog != null)
            m_analyzeDialog.setOnlySupported(onlySupported);
    }

    public void cbAnalyzeSort()
    {
        boolean sort = m_menuBar.getAnalyzeSort();
        m_prefs.putBoolean("analyze-sort", sort);
        if (m_analyzeDialog != null)
            m_analyzeDialog.setSort(sort);
    }

    public void cbAttachProgram()
    {        
        String program = SelectProgram.select(this);
        if (program == null)
            return;
        if (m_gtp != null)
            if (! cbDetachProgram())
                return;
        if (! attachProgram(program))
        {
            m_prefs.put("program", "");
            return;
        }
        m_prefs.put("program", m_program);
        if (m_gtpShell != null && m_session.isVisible("shell"))
        {
            m_menuBar.setShowShell(true);
            cbShowShell();
        }
        if (m_session.isVisible("analyze"))
        {
            m_menuBar.setShowAnalyze(true);
            cbAnalyze();
        }
        toFrontLater();
    }

    public void cbAutoNumber(boolean enable)
    {
        if (m_gtp != null)
            m_gtp.setAutoNumber(enable);
    }

    public void cbBackward(int n)
    {
        backward(n);
        boardChangedBegin(false, false);
    }

    public void cbBeginning()
    {
        backward(NodeUtil.getDepth(m_currentNode));
        boardChangedBegin(false, false);
    }

    public void cbCommandCompletion()
    {
        if (m_gtpShell == null)
            return;
        boolean commandCompletion = m_menuBar.getCommandCompletion();
        m_gtpShell.setCommandCompletion(commandCompletion);
        m_prefs.putBoolean("gtpshell-disable-completions",
                           ! commandCompletion);
    }

    public boolean cbDetachProgram()
    {        
        if (m_gtp == null)
            return false;
        if (isCommandInProgress() && ! showQuestion("Kill program?"))
            return false;
        detachProgram();
        m_prefs.put("program", "");
        return true;
    }

    public void cbEnd()
    {
        forward(NodeUtil.getNodesLeft(m_currentNode));
        boardChangedBegin(false, false);
    }

    public void cbForward(int n)
    {
        forward(n);
        boardChangedBegin(false, false);
    }

    public void cbGotoNode(ConstNode node)
    {
        gotoNode(node);
        boardChangedBegin(false, false);
    }

    public void cbGtpShellSave()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.saveLog(this);
    }

    public void cbGtpShellSaveCommands()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.saveCommands(this);
    }

    public void cbGtpShellSendFile()
    {
        if (m_gtpShell == null)
            return;
        File file = SimpleDialogs.showOpen(this, "Choose GTP file");
        if (file == null)
            return;
        sendGtpFile(file);
        m_menuBar.addRecentGtp(file);
    }

    public void cbNextVariation()
    {
        ConstNode node = NodeUtil.getNextVariation(m_currentNode);
        if (node != null)
            cbGotoNode(node);
    }

    public void cbNextEarlierVariation()
    {
        ConstNode node = NodeUtil.getNextEarlierVariation(m_currentNode);
        if (node != null)
            cbGotoNode(node);
    }

    public void cbPreviousVariation()
    {
        ConstNode node = NodeUtil.getPreviousVariation(m_currentNode);
        if (node != null)
            cbGotoNode(node);
    }

    public void cbPreviousEarlierVariation()
    {
        ConstNode node = NodeUtil.getPreviousEarlierVariation(m_currentNode);
        if (node != null)
            cbGotoNode(node);
    }

    public void cbShowShell()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.setVisible(m_menuBar.getShowShell());
    }

    public void cbShowInfoPanel()
    {
        if (GuiUtil.isNormalSizeMode(this))
        {
            if (m_showInfoPanel)
                m_comment.setPreferredSize(m_comment.getSize());
            m_guiBoard.setPreferredFieldSize(m_guiBoard.getFieldSize());
        }
        showInfoPanel();
    }

    public void cbShowToolbar()
    {
        if (GuiUtil.isNormalSizeMode(this))
        {
            if (m_showInfoPanel)
                m_comment.setPreferredSize(m_comment.getSize());
            m_guiBoard.setPreferredFieldSize(m_guiBoard.getFieldSize());
        }
        showToolbar();
    }

    public void cbShowTree()
    {
        if (m_menuBar.getShowTree())
        {
            if (m_gameTreeViewer == null)
            {
                m_gameTreeViewer = new GameTreeViewer(this, this);
                m_gameTreeViewer.setLabelMode(m_menuBar.getGameTreeLabels());
                m_gameTreeViewer.setSizeMode(m_menuBar.getGameTreeSize());
                boolean showSubtreeSizes = m_menuBar.getShowSubtreeSizes();
                m_gameTreeViewer.setShowSubtreeSizes(showSubtreeSizes);
                restoreSize(m_gameTreeViewer, "tree");
            }
            updateGameTree(true);
            if (m_gameTreeViewer != null) // updateGameTree can close viewer
                m_gameTreeViewer.setVisible(true);
            return;
        }
        else
            disposeGameTree();
    }

    public void clearAnalyzeCommand()
    {
        clearAnalyzeCommand(true);
    }

    public void clearAnalyzeCommand(boolean resetBoard)
    {
        if (m_analyzeCommand != null)
        {
            if (isCommandInProgress())
            {
                showError("Cannot clear analyze command\n" +
                          "while command in progress");
                return;
            }
            if (m_setupMode)
            {
                showError("Cannot clear analyze command\n" +
                          "in setup mode");
                return;
            }
            m_analyzeCommand = null;
            setBoardCursorDefault();
        }
        if (resetBoard)
        {
            resetBoard();
            clearStatus();
        }
    }

    public void contextMenu(GoPoint point, Component invoker, int x, int y)
    {
        if (isCommandInProgress())
            return;
        if (m_setupMode
            || (m_analyzeCommand != null
                && m_analyzeCommand.needsPointListArg()))
        {
            fieldClicked(point, true);
            return;
        }
        ContextMenu contextMenu = createContextMenu(point);
        contextMenu.show(invoker, x, y);
    }

    public void disposeGameTree()
    {
        if (m_gameTreeViewer == null)
            return;
        m_gameTreeViewer.dispose();
        m_gameTreeViewer = null;
        m_menuBar.setShowTree(false);
    }

    public void fieldClicked(GoPoint p, boolean modifiedSelect)
    {
        if (isCommandInProgress())
            return;
        if (m_setupMode)
        {
            GoColor toMove = m_board.getToMove();
            GoColor color;
            if (modifiedSelect)
                color = toMove.otherColor();
            else
                color = toMove;
            if (m_board.getColor(p) == color)
                color = GoColor.EMPTY;
            m_board.setup(p, color);
            m_board.setToMove(toMove);
            updateGameInfo(true);
            updateFromGoBoard();
            setModified(true);
        }
        else if (m_analyzeCommand != null && m_analyzeCommand.needsPointArg()
                 && ! modifiedSelect)
        {
            m_analyzeCommand.setPointArg(p);
            m_guiBoard.clearAllSelect();
            m_guiBoard.setSelect(p, true);
            analyzeBegin(false, false);
            return;
        }
        else if (m_analyzeCommand != null
                 && m_analyzeCommand.needsPointListArg())
        {
            ArrayList pointListArg = m_analyzeCommand.getPointListArg();
            if (pointListArg.contains(p))
            {
                pointListArg.remove(p);
                if (modifiedSelect)
                    pointListArg.add(p);
            }
            else
                pointListArg.add(p);
            m_guiBoard.clearAllSelect();
            GuiBoardUtil.setSelect(m_guiBoard, pointListArg, true);
            if (modifiedSelect && pointListArg.size() > 0)
                analyzeBegin(false, false);
            return;
        }
        else if (m_scoreMode && ! modifiedSelect)
        {
            GuiBoardUtil.scoreSetDead(m_guiBoard, m_countScore, m_board, p);
            double komi = m_gameTree.getGameInformation().getKomi();
            m_scoreDialog.showScore(m_countScore.getScore(komi, getRules()));
            return;
        }
        else if (modifiedSelect)
            m_guiBoard.contextMenu(p);
        else
        {
            if (m_board.isSuicide(p, m_board.getToMove())
                && ! showQuestion("Play suicide?"))
                return;
            else if (m_board.isKo(p)
                && ! showQuestion("Play illegal Ko move?"))
                return;
            Move move = Move.get(p, m_board.getToMove());
            humanMoved(move);
        }
    }

    public boolean sendGtpCommand(String command, boolean sync)
        throws GtpError
    {
        if (isCommandInProgress() || m_gtp == null)
            return false;
        if (! checkProgramInSync())
            return false;
        if (m_setupMode)
        {
            showError("Cannot send command in setup mode");
            return false;
        }
        if (sync)
        {
            m_gtp.send(command);
            return true;
        }
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    sendGtpCommandContinue();
                }
            };
        beginLengthyCommand();
        m_gtp.send(command, callback);
        return true;
    }

    public void sendGtpCommandContinue()
    {
        endLengthyCommand();
    }

    public void initAnalyzeCommand(AnalyzeCommand command, boolean autoRun)
    {
        if (m_gtp == null)
            return;
        if (! checkProgramInSync())
            return;
        m_analyzeCommand = command;
        m_analyzeAutoRun = autoRun;
        if (command.needsPointArg())
        {
            setBoardCursor(Cursor.HAND_CURSOR);
            showStatusSelectTarget();
        }
        else if (command.needsPointListArg())
        {
            setBoardCursor(Cursor.HAND_CURSOR);
            showStatusSelectPointList();
        }
    }

    public void setAnalyzeCommand(AnalyzeCommand command, boolean autoRun,
                                  boolean clearBoard, boolean oneRunOnly)
    {
        if (isCommandInProgress())
        {
            showError("Cannot run analyze command\n" +
                      "while command in progress");
            return;
        }
        if (! checkProgramInSync())
            return;
        if (m_setupMode)
        {
            showError("Cannot run analyze command\n" +
                      "in setup mode");
            return;
        }
        initAnalyzeCommand(command, autoRun);
        m_analyzeOneRunOnly = oneRunOnly;
        boolean needsPointArg = m_analyzeCommand.needsPointArg();
        if (needsPointArg && ! m_analyzeCommand.isPointArgMissing())
        {
            m_guiBoard.clearAllSelect();
            m_guiBoard.setSelect(m_analyzeCommand.getPointArg(), true);
        }
        else if (needsPointArg || m_analyzeCommand.needsPointListArg())
        {
            m_guiBoard.clearAllSelect();
            if (m_analyzeCommand.getType() == AnalyzeCommand.EPLIST)
                GuiBoardUtil.setSelect(m_guiBoard,
                                        m_analyzeCommand.getPointListArg(),
                                        true);
            toFront();
            return;
        }
        analyzeBegin(false, clearBoard);
    }    

    private class AnalyzeContinue
        implements Runnable
    {
        public AnalyzeContinue(boolean checkComputerMove,
                               boolean resetBoard)
        {
            m_checkComputerMove = checkComputerMove;
            m_resetBoard = resetBoard;
        }

        public void run()
        {
            analyzeContinue(m_checkComputerMove, m_resetBoard);
        }
        
        private final boolean m_checkComputerMove;

        private final boolean m_resetBoard;
    }

    private class ShowInvalidResponse
        implements Runnable
    {
        public ShowInvalidResponse(String line)
        {
            m_line = line;
        }

        public void run()
        {
            if (m_line.trim().equals(""))
                showWarning("Invalid empty response line");
            else
                showWarning("Invalid response:\n" + m_line);
        }
        
        private final String m_line;
    }

    private static class LoadFileRunnable
        implements GuiUtil.ProgressRunnable
    {
        LoadFileRunnable(FileInputStream in, File file)
        {
            m_in = in;
            m_file = file;            
        }

        public SgfReader getReader()
        {
            return m_reader;
        }

        public void run(ProgressShow progressShow) throws Throwable
        {
            m_reader = new SgfReader(m_in, m_file.toString(), progressShow,
                                     m_file.length());
        }

        private final File m_file;

        private final FileInputStream m_in;

        private SgfReader m_reader;
    }

    private boolean m_analyzeAutoRun;

    private boolean m_analyzeOneRunOnly;

    private final boolean m_auto;

    private boolean m_beepAfterMove;

    private boolean m_computerBlack;

    private boolean m_computerWhite;

    private boolean m_ignoreInvalidResponses;

    /** State variable used between cbInterrupt and computerMoved. */
    private boolean m_interruptComputerBoth;

    /** State variable used between generateMove and computerMoved. */
    private boolean m_isSingleMove;

    private boolean m_lostOnTimeShown;

    /** Flag that m_gameTree was modified after creation or loading from
        file.
    */
    private boolean m_modified;

    private boolean m_resigned;

    private boolean m_scoreMode;

    private boolean m_setupMode;

    private boolean m_showInfoPanel;

    private boolean m_showLastMove;

    private boolean m_showToolbar;

    private boolean m_showVariations;

    private final boolean m_verbose;

    private int m_boardSize;

    private int m_handicap;

    private final int m_move;

    /** Serial version to suppress compiler warning.
        Contains a marker comment for use with serialver.sourceforge.net
    */
    private static final long serialVersionUID = 0L; // SUID

    private final Board m_board;

    private final BoardUpdater m_boardUpdater = new BoardUpdater();

    private final GuiBoard m_guiBoard;

    private final Clock m_clock;

    private GuiGtpClient m_gtp;

    private final Comment m_comment;

    /** Last loaded or saved file. */
    private File m_loadedFile;

    private final GameInfo m_gameInfo;    

    private GtpShell m_gtpShell;

    private GameTree m_gameTree;

    private GameTreeViewer m_gameTreeViewer;

    private Help m_help;

    private final JPanel m_infoPanel;

    private final JPanel m_innerPanel;

    private final JSplitPane m_splitPane;

    private final GoGuiMenuBar m_menuBar;

    private Node m_currentNode;

    private OptionalMessage m_gameFinishedMessage;

    private OptionalMessage m_overwriteWarning;

    private OptionalMessage m_passWarning;

    private OptionalMessage m_saveQuestion;

    private Pattern m_pattern;

    private AnalyzeCommand m_analyzeCommand;

    private final File m_file;

    private final Session m_session = new Session("");

    private final CountScore m_countScore = new CountScore();

    private final StatusBar m_statusBar;

    private final String m_gtpCommand;

    private final String m_gtpFile;

    private final String m_initAnalyze;

    private String m_lastAnalyzeCommand;

    private String m_name;

    private String m_program;

    private String m_titleFromProgram;

    private String m_version = "";

    private AnalyzeDialog m_analyzeDialog;    

    private final Preferences m_prefs =
        Preferences.userNodeForPackage(getClass());

    private ScoreDialog m_scoreDialog;

    private String m_programAnalyzeCommands;

    private final ThumbnailCreator m_thumbnailCreator =
        new ThumbnailCreator(false);

    private TimeSettings m_timeSettings;

    private final GoGuiToolBar m_toolBar;

    private ArrayList m_bookmarks;

    private void analyzeBegin(boolean checkComputerMove, boolean resetBoard)
    {
        if (m_gtp == null || m_analyzeCommand == null
            || m_analyzeCommand.isPointArgMissing())
            return;
        showStatus("Running " + m_analyzeCommand.getResultTitle() + "...");
        GoColor toMove = m_board.getToMove();
        m_lastAnalyzeCommand = m_analyzeCommand.replaceWildCards(toMove);
        runLengthyCommand(m_lastAnalyzeCommand,
                          new AnalyzeContinue(checkComputerMove, resetBoard));
    }

    private void analyzeContinue(boolean checkComputerMove,
                                 boolean resetBoard)
    {
        if (resetBoard)
            resetBoard();
        if (! endLengthyCommand())
            return;
        String title = m_analyzeCommand.getResultTitle();
        try
        {
            boolean statusContainsResponse = false;
            String response = m_gtp.getResponse();
            String statusText = AnalyzeShow.show(m_analyzeCommand, m_guiBoard,
                                                 m_board, response);
            if (statusText != null)
            {
                m_statusBar.setText(statusText);
                statusContainsResponse = true;
            }
            int type = m_analyzeCommand.getType();
            GoPoint pointArg = null;
            if (m_analyzeCommand.needsPointArg())
                pointArg = m_analyzeCommand.getPointArg();
            else if (m_analyzeCommand.needsPointListArg())
            {
                ArrayList list = m_analyzeCommand.getPointListArg();
                if (list.size() > 0)
                    pointArg = (GoPoint)list.get(list.size() - 1);
            }
            if (type == AnalyzeCommand.PARAM)
                ParameterDialog.editParameters(m_lastAnalyzeCommand, this,
                                               title, response,
                                               m_gtp);
            if (AnalyzeCommand.isTextType(type))
            {
                if (response.indexOf("\n") < 0)
                {
                    showStatus(title + ": " + response);
                    statusContainsResponse = true;
                }
                else
                    GoGuiUtil.showAnalyzeTextOutput(this, m_guiBoard, type,
                                                     pointArg, title,
                                                     response);
            }
            if (! statusContainsResponse && type != AnalyzeCommand.PARAM)
                showStatus(title);
            if (checkComputerMove)
                checkComputerMove();
        }
        catch (GtpError e)
        {                
            showStatus(title);
            showError(e);
        }
        finally
        {
            if (m_analyzeOneRunOnly)
                clearAnalyzeCommand(false);
        }
    }

    /** Attach program.
        @return true if program was successfully attached.
    */
    private boolean attachProgram(String program)
    {
        program = program.trim();
        if (program.equals(""))
            return false;
        m_program = program;
        if (m_gtpShell != null)
        {
            m_gtpShell.dispose();
            m_gtpShell = null;
        }
        m_gtpShell = new GtpShell(this, this);
        m_gtpShell.addWindowListener(new WindowAdapter()
            {
                public void windowClosing(WindowEvent e)
                {
                    m_menuBar.setShowShell(false);
                }
            });
        m_gtpShell.setProgramCommand(program);
        m_gtpShell.setTimeStamp(m_menuBar.getTimeStamp());
        m_gtpShell.setCommandCompletion(m_menuBar.getCommandCompletion());
        m_ignoreInvalidResponses = false;
        GtpClient.InvalidResponseCallback invalidResponseCallback =
            new GtpClient.InvalidResponseCallback()
            {
                public void show(String line)
                {
                    if (m_ignoreInvalidResponses)
                        return;
                    m_ignoreInvalidResponses = true;
                    Runnable runnable = new ShowInvalidResponse(line);
                    if (SwingUtilities.isEventDispatchThread())
                        runnable.run();
                    else
                        invokeAndWait(runnable);
                }
            };
        GtpClient.IOCallback ioCallback = new GtpClient.IOCallback()
            {
                public void receivedInvalidResponse(String s)
                {
                    m_gtpShell.receivedInvalidResponse(s);
                }

                public void receivedResponse(boolean error, String s)
                {
                    m_gtpShell.receivedResponse(error, s);
                }

                public void receivedStdErr(String s)
                {
                    m_gtpShell.receivedStdErr(s);
                    m_liveGfx.receivedStdErr(s);
                }

                public void sentCommand(String s)
                {
                    m_gtpShell.sentCommand(s);
                }

                private LiveGfx m_liveGfx =
                    new LiveGfx(m_board, m_guiBoard, m_statusBar);
            };
        GtpSynchronizer.Callback synchronizerCallback =
            new GtpSynchronizer.Callback()
            {
                public void run(int moveNumber)
                {
                    m_gameInfo.fastUpdateMoveNumber("[" + moveNumber + "]");
                }
            };
        try
        {
            GtpClient gtp = new GtpClient(m_program, m_verbose, ioCallback);
            gtp.setInvalidResponseCallback(invalidResponseCallback);
            gtp.setAutoNumber(m_menuBar.getAutoNumber());
            m_gtp = new GuiGtpClient(gtp, this, synchronizerCallback);
            m_gtp.start();
        }
        catch (GtpError e)
        {
            showError(e);
            m_toolBar.setComputerEnabled(false);
            m_menuBar.setComputerEnabled(false);
            return false;
        }
        m_menuBar.setComputerEnabled(true);
        m_toolBar.setComputerEnabled(true);
        m_name = null;
        m_titleFromProgram = null;
        try
        {
            m_name = m_gtp.send("name").trim();
        }
        catch (GtpError e)
        {
        }
        if (m_name == null)
            m_name = "Unknown Program";
        try
        {
            m_gtp.queryProtocolVersion();
        }
        catch (GtpError e)
        {
        }
        try
        {
            m_version = m_gtp.queryVersion();
            m_gtpShell.setProgramVersion(m_version);
            m_gtp.querySupportedCommands();
            m_gtp.queryInterruptSupport();
        }
        catch (GtpError e)
        {
        }        
        boolean cleanupSupported
            = m_gtp.isCommandSupported("kgs-genmove_cleanup")
            || m_gtp.isCommandSupported("genmove_cleanup");
        m_menuBar.enableCleanup(cleanupSupported);
        initProgramAnalyzeCommands();
        restoreSize(m_gtpShell, "shell");
        m_gtpShell.setProgramName(m_name);
        ArrayList supportedCommands =
            m_gtp.getSupportedCommands();
        m_gtpShell.setInitialCompletions(supportedCommands);
        if (! m_gtpFile.equals(""))
            sendGtpFile(new File(m_gtpFile));
        if (! m_gtpCommand.equals(""))
            sendGtpString(m_gtpCommand);
        Node oldCurrentNode = m_currentNode;
        m_board.init(m_boardSize);
        if (executeRoot())
            gotoNode(oldCurrentNode);
        setTitle();
        return true;
    }    

    /** Go backward a number of nodes in the tree. */
    private void backward(int n)
    {
        if (n == 0)
            return;
        for (int i = 0; i < n && m_currentNode != m_gameTree.getRoot(); ++i)
            m_currentNode = m_currentNode.getFather();
        currentNodeChanged();
    }

    private void beginLengthyCommand()
    {
        setBoardCursor(Cursor.WAIT_CURSOR);
        m_menuBar.setCommandInProgress();
        m_toolBar.setCommandInProgress();
        m_gtpShell.setCommandInProgess(true);
    }

    private void boardChangedBegin(boolean doCheckComputerMove,
                                   boolean gameTreeChanged)
    {
        updateFromGoBoard();
        updateGameInfo(gameTreeChanged);
        m_toolBar.update(m_currentNode);
        updateMenuBar();
        m_menuBar.selectBoardSizeItem(m_board.getSize());
        if (m_gtp != null
            && ! isOutOfSync()
            && m_analyzeCommand != null
            && m_analyzeAutoRun
            && ! m_analyzeCommand.isPointArgMissing())
            analyzeBegin(doCheckComputerMove, true);
        else
        {
            resetBoard();
            showToMove();
            if (doCheckComputerMove)
                checkComputerMove();
        }
    }

    private void cbAbout()
    {
        String protocolVersion = null;
        String command = null;
        if (m_gtp != null)
        {
            protocolVersion =
                Integer.toString(m_gtp.getProtocolVersion());
            command = m_gtp.getProgramCommand();
        }
        AboutDialog.show(this, m_name, m_version, protocolVersion, command);
    }

    private void cbAddBookmark()
    {
        if (m_loadedFile == null)
        {
            showError("Cannot set bookmark if no file loaded");
            return;
        }
        if (m_modified)
        {
            showError("Cannot set bookmark if file modified");
            return;
        }
        if (m_currentNode.getFather() != null
            && m_currentNode.getMove() == null)
        {
            showError("Cannot set bookmark at non-root node without move");
            return;
        }
        String variation = NodeUtil.getVariationString(m_currentNode);
        int move = NodeUtil.getMoveNumber(m_currentNode);
        Bookmark bookmark = new Bookmark(m_loadedFile, move, variation);
        if (! BookmarkDialog.show(this, "Add Bookmark", bookmark, true))
            return;
        m_bookmarks.add(bookmark);
        m_menuBar.setBookmarks(m_bookmarks);
    }

    private void cbAutoNumber()
    {
        if (m_gtp == null)
            return;
        boolean enable = m_menuBar.getAutoNumber();
        m_gtp.setAutoNumber(enable);
        m_prefs.putBoolean("gtpshell-autonumber", enable);
    }

    private void cbBeepAfterMove()
    {
        m_beepAfterMove = m_menuBar.getBeepAfterMove();
        m_prefs.putBoolean("beep-after-move", m_beepAfterMove);
    }

    private void cbBackToMainVar()
    {
        ConstNode node = NodeUtil.getBackToMainVariation(m_currentNode);
        cbGotoNode(node);
    }

    private void cbBoardSize(String size)
    {
        try
        {
            saveSession();
            cbNewGame(Integer.parseInt(size));
            m_clock.reset();
            m_clock.halt();
            m_gameInfo.updateTimeFromClock(m_clock);
            updateMenuBar();
        }
        catch (NumberFormatException e)
        {
            assert(false);
        }
    }

    private void cbBoardSizeOther()
    {
        int size = BoardSizeDialog.show(this, m_boardSize);
        if (size < 1 || size > GoPoint.MAXSIZE)
            return;
        saveSession();
        cbNewGame(size);
        m_clock.reset();
        m_clock.halt();
            updateMenuBar();
        m_gameInfo.updateTimeFromClock(m_clock);
    }
    
    private void cbBookmark(String number)
    {
        if (! checkSaveGame())
            return;
        try
        {
            int n = Integer.parseInt(number);
            if (n < 0 || n >= m_bookmarks.size())
            {
                assert(false);
                return;
            }
            Bookmark bookmark = (Bookmark)m_bookmarks.get(n);
            File file = bookmark.m_file;
            if (m_loadedFile == null || ! file.equals(m_loadedFile))
                if (! loadFile(file, -1))
                    return;
            String variation = bookmark.m_variation;
            ConstNode node = m_gameTree.getRoot();
            if (! variation.equals(""))
            {
                node = NodeUtil.findByVariation(node, variation);
                if (node == null)
                {
                    showError("Bookmark has invalid variation");
                    return;
                }
            }
            node = NodeUtil.findByMoveNumber(node, bookmark.m_move);
            if (node == null)
            {
                showError("Bookmark has invalid move number");
                return;
            }
            gotoNode(node);
            boardChangedBegin(false, true);
        }
        catch (NumberFormatException e)
        {
            assert(false);
        }
    }

    private void cbClockHalt()
    {
        if (! m_clock.isRunning())
            return;
        m_clock.halt();
        updateMenuBar();
    }

    private void cbClockResume()
    {
        if (m_clock.isRunning())
            return;
        m_clock.startMove(m_board.getToMove());
        updateMenuBar();
    }

    private void cbClockRestore()
    {        
        GoColor color = m_board.getToMove();
        clockRestore(m_currentNode, color.otherColor());
        Node father = m_currentNode.getFather();
        if (father != null)
            clockRestore(father, color);
        m_gameInfo.updateTimeFromClock(m_clock);
        updateMenuBar();
    }

    private void cbCommentChanged()
    {
        setModified(true);
        if (m_gameTreeViewer != null)
            m_gameTreeViewer.redrawCurrentNode();
    }

    private void cbCommentFontFixed()
    {
        boolean fixed = m_menuBar.getCommentFontFixed();
        m_comment.setFontFixed(fixed);
        m_prefs.putBoolean("comment-font-fixed", fixed);
    }

    private void cbComputerBoth()
    {
        computerBoth();
        if (! isCommandInProgress())
            checkComputerMove();
    }

    private void cbEditBookmarks()
    {
        if (! EditBookmarksDialog.show(this, m_bookmarks))
            return;
        m_menuBar.setBookmarks(m_bookmarks);
    }

    private void cbExportAscii()
    {
        File file = SimpleDialogs.showSave(this, "Export Text Diagram");
        if (file == null)
            return;
        try
        {
            String text = BoardUtil.toString(m_board, false);
            PrintStream out = new PrintStream(new FileOutputStream(file));
            out.print(text);
            out.close();
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    private void cbExportClipboard()
    {
        String text = BoardUtil.toString(m_board, false);
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        ClipboardOwner owner = new ClipboardOwner() {
                public void lostOwnership(Clipboard clipboard,
                                          Transferable contents)
                {
                }
            };
        clipboard.setContents(selection, owner);
    }

    private void cbExportSgfPosition()
    {
        File file = SimpleDialogs.showSaveSgf(this);
        if (file == null)
            return;
        try
        {
            savePosition(file);
        }
        catch (FileNotFoundException e)
        {
            showError("Could not save position", e);
        }
    }

    private void cbExportLatex()
    {
        File file = SimpleDialogs.showSave(this, "Export LaTeX");
        if (file == null)
            return;
        try
        {
            OutputStream out = new FileOutputStream(file);
            String title = FileUtil.removeExtension(new File(file.getName()),
                                                     "tex");
            new TexWriter(title, out, m_gameTree, false);
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    private void cbExportLatexPosition()
    {
        File file = SimpleDialogs.showSave(this, "Export LaTeX Position");
        if (file == null)
            return;
        try
        {
            OutputStream out = new FileOutputStream(file);
            String title = FileUtil.removeExtension(new File(file.getName()),
                                                     "tex");
            new TexWriter(title, out, m_board, false,
                          GuiBoardUtil.getLabels(m_guiBoard),
                          GuiBoardUtil.getMarkSquare(m_guiBoard),
                          GuiBoardUtil.getSelects(m_guiBoard));
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    private void cbFindInComments()
    {
        Pattern pattern = FindDialog.run(this, m_comment.getSelectedText());
        if (pattern == null)
            return;
        m_pattern = pattern;
        m_menuBar.enableFindNext(true);
        if (NodeUtil.commentContains(m_currentNode, m_pattern))
            m_comment.markAll(m_pattern);
        else
            cbFindNext();
    }

    private void cbFindNext()
    {
        if (m_pattern == null)
            return;
        ConstNode root = m_gameTree.getRoot();
        ConstNode node = NodeUtil.findInComments(m_currentNode, m_pattern);
        if (node == null)
            if (m_currentNode != root)
                if (showQuestion("End of tree reached. Continue from start?"))
                {
                    node = root;
                    if (! NodeUtil.commentContains(node, m_pattern))
                        node = NodeUtil.findInComments(node, m_pattern);
                }
        if (node == null)
        {
            showInfo("Not found");
            m_menuBar.enableFindNext(false);
        }
        else
        {
            cbGotoNode(node);
            m_comment.markAll(m_pattern);
        }
    }

    private void cbGameInfo()
    {
        GameInformation gameInformation = m_gameTree.getGameInformation();
        if (! GameInfoDialog.show(this, gameInformation))
            return;
        if (! gameInformation.komiEquals(m_prefs.getDouble("komi", 6.5)))
        {
            m_prefs.putDouble("komi", gameInformation.getKomi());
            setKomi(gameInformation.getKomi());
        }
        if (gameInformation.getRules() != null
            && ! gameInformation.getRules().equals(m_prefs.get("rules", "")))
        {
            m_prefs.put("rules", gameInformation.getRules());
            setRules();
        }
        m_timeSettings = gameInformation.getTimeSettings();
        setTitle();
    }

    private void cbGameTreeLabels(int mode)
    {
        m_prefs.putInt("gametree-labels", mode);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setLabelMode(mode);
            updateGameTree(true);
        }
    }

    private void cbGameTreeSize(int mode)
    {
        m_prefs.putInt("gametree-size", mode);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setSizeMode(mode);
            updateGameTree(true);
        }
    }

    private void cbGameTreeShowSubtreeSizes()
    {
        boolean enable = m_menuBar.getShowSubtreeSizes();
        m_prefs.putBoolean("gametree-show-subtree-sizes", enable);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setShowSubtreeSizes(enable);
            updateGameTree(true);
        }
    }

    private void cbGoto()
    {
        ConstNode node = MoveNumberDialog.show(this, m_currentNode);
        if (node == null)
            return;
        cbGotoNode(node);
    }

    private void cbGotoVariation()
    {
        ConstNode node = GotoVariationDialog.show(this, m_gameTree,
                                                  m_currentNode);
        if (node == null)
            return;
        cbGotoNode(node);
    }

    private void cbHandicap(String handicap)
    {
        try
        {
            m_handicap = Integer.parseInt(handicap);
            if (m_board.isModified())
                showInfo("Handicap will take effect on next game.");
            else
            {
                computerBlack();
                newGame(m_boardSize);
            }
        }
        catch (NumberFormatException e)
        {
            assert(false);
        }
    }

    private void cbHelp()
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource("net/sf/gogui/doc/index.html");
        if (url == null)
        {
            showError("Help not found");
            return;
        }
        if (Platform.isGnuClasspath())
        {
            // JEditorPane does not display HTML in GNU classpath 0.90 yet,
            // so we invoke a browser. Cannot use internal files from JAR
            // though
            File file = null;
            String s = url.toString();
            int pos = s.indexOf("lib/gogui.jar!/");
            if (pos >= 0 && s.startsWith("jar:file:"))
            {
                String path = s.substring(9, pos);
                File file1 = new File(path, "doc/manual/html/index.html");
                File file2 = new File(path, "../doc/gogui/index.html");
                if (file1.exists())
                    file = file1;
                else if (file2.exists())
                    file = file2;
                else
                {
                    showError("Documentation not found in:\n"
                              + file1.toString() + "\n"
                              + file2.toString());
                    return;
                }
                
            }
            else
            {
                showError("Cannot show documentation " +
                          " (must run from gogui.jar)");
                return;
            }
            try
            {
                if (! Platform.openInExternalBrowser(file.toURI().toURL()))
                    showError("Could not open browser");
            }
            catch (MalformedURLException e)
            {
                assert(false);
            }
            return;
        }
        if (m_help == null)
        {
            m_help = new Help(this, url);
            restoreSize(m_help, "help");
        }
        m_help.setVisible(true);
        m_help.toFront();
    }

    private void cbInterrupt()
    {
        if (! isCommandInProgress() || m_gtp == null
            || m_gtp.isProgramDead())
            return;
        if (Interrupt.run(this, m_gtp))
            showStatus("Interrupting...");
    }

    private void cbKeepOnlyMainVariation()
    {
        if (! NodeUtil.isInMainVariation(m_currentNode))
            return;
        if (! showQuestion("Delete all variations but main?"))
            return;
        m_gameTree.keepOnlyMainVariation();
        setModified(true);
        boardChangedBegin(false, true);
    }

    private void cbKeepOnlyPosition()
    {
        if (! showQuestion("Delete all moves?"))
            return;
        GameInformation info = m_gameTree.getGameInformation();
        m_gameTree = NodeUtil.makeTreeFromPosition(info, m_board);
        m_board.init(m_boardSize);
        executeRoot();
        setModified(true);
        boardChangedBegin(false, true);
    }

    private void cbMakeMainVariation()
    {
        if (! showQuestion("Make current to main variation?"))
            return;
        NodeUtil.makeMainVariation(m_currentNode);
        setModified(true);
        boardChangedBegin(false, true);
    }

    private void cbNewGame(int size)
    {
        if (! checkSaveGame())
            return;
        m_prefs.putInt("boardsize", size);
        clearLoadedFile();
        newGame(size);
        computerWhite();
        m_clock.startMove(GoColor.BLACK);
        updateMenuBar();
        boardChangedBegin(true, true);
    }

    private void cbOpen()
    {
        if (! checkSaveGame())
            return;
        File file = SimpleDialogs.showOpenSgf(this);
        if (file == null)
            return;
        m_menuBar.addRecent(file);
        loadFile(file, -1);
        boardChangedBegin(false, true);
    }

    private void cbPass()
    {
        if (isCommandInProgress())
            return;
        if (m_passWarning == null)
            m_passWarning = new OptionalMessage(this);
        if (! m_passWarning.showQuestion("Really pass?"))
            return;
        humanMoved(Move.getPass(m_board.getToMove()));
    }

    private void cbPlay(boolean isSingleMove)
    {
        if (m_gtp == null || isCommandInProgress())
            return;
        if (! checkProgramInSync())
            return;
        if (! isSingleMove && ! isComputerBoth())
        {
            if (m_board.getToMove() == GoColor.BLACK)
                computerBlack();
            else
                computerWhite();
        }
        m_interruptComputerBoth = false;
        generateMove(isSingleMove);
        if (m_currentNode == m_gameTree.getRoot()
            && m_currentNode.getNumberChildren() == 0)
            m_clock.reset();
        m_clock.startMove(m_board.getToMove());
    }


    private void cbPrint()
    {
        Print.run(this, m_guiBoard);
    }

    private void cbSave()
    {
        if (m_loadedFile == null)
            saveDialog();
        else
        {
            if (m_loadedFile.exists())
            {
                if (m_overwriteWarning == null)
                    m_overwriteWarning = new OptionalMessage(this);
                String message = "Overwrite " + m_loadedFile + "?";
                if (! m_overwriteWarning.showWarning(message))
                    return;
            }
            save(m_loadedFile);
        }
    }

    private void cbSaveAs()
    {
        saveDialog();
    }

    private void cbScore()
    {
        if (m_gtp == null)
        {
            showInfo("No program is attached.\n" +
                     "Please mark dead groups manually.");
            initScore(null);
            return;
        }
        if (m_gtp.isCommandSupported("final_status_list"))
        {
            Runnable callback = new Runnable()
                {
                    public void run()
                    {
                        cbScoreContinue();
                    }
                };
            showStatus("Scoring...");
            runLengthyCommand("final_status_list dead", callback);
        }
        else
        {
            showInfo(m_name + " does not support scoring.\n" +
                     "Please mark dead groups manually.");
            initScore(null);
        }
    }

    private void cbScoreContinue()
    {
        boolean success = endLengthyCommand();
        clearStatus();
        GoPoint[] isDeadStone = null;
        if (success)
        {
            String response = m_gtp.getResponse();
            try
            {
                isDeadStone = GtpUtil.parsePointList(response, m_boardSize);
            }
            catch (GtpError error)
            {
                showError(error);
            }
        }
        initScore(isDeadStone);
    }    

    private void cbScoreDone(boolean accepted)
    {
        m_scoreDialog.setVisible(false);
        if (accepted)
        {
            double komi = m_gameTree.getGameInformation().getKomi();
            setResult(m_countScore.getScore(komi, getRules()).formatResult());
        }
        clearStatus();
        m_guiBoard.clearAll();
        m_scoreMode = false;
        m_toolBar.enableAll(true, m_currentNode);
        m_menuBar.setNormalMode();
    }

    private void cbSetup()
    {
        if (m_setupMode)
        {
            setupDone();
            return;
        }
        if (! checkSaveGame())
            return;
        m_menuBar.setSetupMode();
        m_showLastMove = false;
        if (m_gameTreeViewer != null)
        {
            // Create a dummy game tree, so that GameTreeDialog shows
            // a setup node
            m_gameTree = new GameTree(m_boardSize, 0, null, null, null);
            setCurrentNode(m_gameTree.getRoot());
            m_currentNode.addBlack(GoPoint.get(0, 0));
            m_clock.reset();
            updateGameInfo(true);
        }
        resetBoard();
        m_setupMode = true;
        m_toolBar.enableAll(false, null);
        showStatus("Setup Black");
        m_board.setToMove(GoColor.BLACK);
    }

    private void cbSetupBlack()
    {
        showStatus("Setup Black");
        m_board.setToMove(GoColor.BLACK);
        updateGameInfo(false);
    }

    private void cbSetupWhite()
    {
        showStatus("Setup White");
        m_board.setToMove(GoColor.WHITE);
        updateGameInfo(false);
    }

    private void cbShowCursor()
    {
        boolean showCursor = m_menuBar.getShowCursor();
        m_guiBoard.setShowCursor(showCursor);
        m_prefs.putBoolean("show-cursor", showCursor);
    }

    private void cbShowGrid()
    {
        boolean showGrid = m_menuBar.getShowGrid();
        m_guiBoard.setShowGrid(showGrid);
        m_prefs.putBoolean("show-grid", showGrid);
    }

    private void cbShowLastMove()
    {
        m_showLastMove = m_menuBar.getShowLastMove();
        m_prefs.putBoolean("show-last-move", m_showLastMove);
        updateFromGoBoard();
        updateGameInfo(false);
    }

    private void cbShowVariations()
    {
        m_showVariations = m_menuBar.getShowVariations();
        m_prefs.putBoolean("show-variations", m_showVariations);
        resetBoard();
        updateGameInfo(false);
    }

    private void cbTimeStamp()
    {
        if (m_gtpShell == null)
            return;
        boolean enable = m_menuBar.getTimeStamp();
        m_gtpShell.setTimeStamp(enable);
        m_prefs.putBoolean("gtpshell-timestamp", enable);
    }

    private void cbTruncate()
    {
        if (m_currentNode.getFather() == null)
            return;
        if (! showQuestion("Truncate current?"))
            return;
        Node oldCurrentNode = m_currentNode;
        backward(1);
        m_currentNode.removeChild(oldCurrentNode);
        setModified(true);
        boardChangedBegin(false, true);
    }

    private void cbTruncateChildren()
    {
        int numberChildren = m_currentNode.getNumberChildren();
        if (numberChildren == 0)
            return;
        if (! showQuestion("Truncate children?"))
            return;
        NodeUtil.truncateChildren(m_currentNode);
        setModified(true);
        boardChangedBegin(false, true);
    }

    private void checkComputerMove()
    {
        if (m_gtp == null || isOutOfSync())
            return;
        int moveNumber = NodeUtil.getMoveNumber(m_currentNode);
        boolean bothPassed = (moveNumber >= 2 && m_board.bothPassed());
        if (bothPassed)
            m_menuBar.setCleanup(true);
        boolean gameFinished = (bothPassed || m_resigned);
        if (isComputerBoth())
        {
            if (gameFinished)
            {
                if (m_auto)
                {
                    newGame(m_boardSize);
                    checkComputerMove();
                    return;
                }
                m_clock.halt();
                showGameFinished();
                return;
            }
            generateMove(false);            
        }
        else
        {
            if (gameFinished)
            {
                m_clock.halt();
                showGameFinished();
                return;
            }
            else if (computerToMove())
                generateMove(false);
        }
    }

    private void checkLostOnTime(GoColor color)
    {
        if (m_clock.lostOnTime(color)
            && ! m_clock.lostOnTime(color.otherColor())
            && ! m_lostOnTimeShown)
        {
            if (color == GoColor.BLACK)
            {
                showInfo("Black lost on time.");
                setResult("W+Time");
            }
            else
            {
                assert(color == GoColor.WHITE);
                showInfo("White lost on time.");
                setResult("B+Time");
            }
            m_lostOnTimeShown = true;
        }
    }

    private boolean checkProgramInSync()
    {
        if (isOutOfSync())
        {
            Object[] options = { "Detach Program", "Cancel" };
            Object message =
                "Could not synchronize current\n" +
                "position with Go program";
            int n = JOptionPane.showOptionDialog(this, message, "Error",
                                                 JOptionPane.YES_NO_OPTION,
                                                 JOptionPane.ERROR_MESSAGE,
                                                 null, options, options[1]);
            if (n == 0)
                cbDetachProgram();
            return false;
        }
        return true;
    }

    /** Ask for saving file if it was modified.
        @return true If file was not modified, user chose not to save it
        or file was saved successfully
    */
    private boolean checkSaveGame()
    {
        if (! m_modified)
            return true;
        if (m_saveQuestion == null)
            m_saveQuestion = new OptionalMessage(this);
        int result =
            m_saveQuestion.showYesNoCancelQuestion("Save current game?");
        switch (result)
        {
        case 0:
            if (m_loadedFile == null)
                return saveDialog();
            else
                return save(m_loadedFile);
        case 1:
            setModified(false);
            return true;
        case 2:
            return false;
        default:
            assert(false);
            return true;
        }
    }
    
    private void clearStatus()
    {
        m_statusBar.clear();
    }

    private void clockRestore(Node node, GoColor color)
    {
        Move move = node.getMove();
        if (move == null)
        {
            if (node == m_gameTree.getRoot())
                m_clock.reset();
            return;
        }
        if (move.getColor() != color)
            return;
        double timeLeft = node.getTimeLeft(color);
        int movesLeft = node.getMovesLeft(color);
        if (! Double.isNaN(timeLeft))
            m_clock.setTimeLeft(color, (long)(timeLeft * 1000), movesLeft);
    }

    private void close()
    {
        if (isCommandInProgress() && ! showQuestion("Kill program?"))
                return;
        if (m_setupMode)
            setupDone();
        if (! checkSaveGame())
            return;
        saveSession();        
        if (m_gtp != null)
        {
            m_analyzeCommand = null;
            detachProgram();
        }
        dispose();
        System.exit(0);
    }

    private void computerBlack()
    {
        m_computerBlack = true;
        m_computerWhite = false;
        m_menuBar.setComputerBlack();
    }

    private void computerBoth()
    {
        m_computerBlack = true;
        m_computerWhite = true;
        m_menuBar.setComputerBoth();
    }

    private void computerMoved()
    {
        if (! endLengthyCommand())
            return;
        if (m_beepAfterMove)
            java.awt.Toolkit.getDefaultToolkit().beep();
        try
        {
            m_clock.stopMove();
            String response = m_gtp.getResponse();
            GoColor toMove = m_board.getToMove();
            checkLostOnTime(toMove);
            boolean gameTreeChanged = false;
            if (response.equalsIgnoreCase("resign"))
            {
                if (! isComputerBoth())
                    showInfo(m_name + " resigns");
                m_resigned = true;
                setResult((toMove == GoColor.BLACK ? "W" : "B") + "+Resign");
            }
            else
            {
                GoPoint point = GtpUtil.parsePoint(response, m_boardSize);
                if (point != null
                    && m_board.getColor(point) != GoColor.EMPTY)
                    showWarning("Program played move on non-empty point");
                Move move = Move.get(point, toMove);
                setModified(true);
                m_board.play(move);
                Node node = createNode(move);
                m_currentNode = node;
                m_gtp.updateAfterGenmove(m_board);
                if (point == null && ! isComputerBoth())
                    showInfo(m_name + " passes");
                m_resigned = false;
                gameTreeChanged = true;
                if (m_currentNode.getFather().getNumberChildren() == 1)
                {
                    if (m_gameTreeViewer != null)
                        m_gameTreeViewer.addNewSingleChild(m_currentNode);
                    gameTreeChanged = false;
                }
            }
            m_clock.startMove(m_board.getToMove());
            updateMenuBar();
            boolean doCheckComputerMove
                = (! m_isSingleMove
                   && ! (isComputerBoth() && m_interruptComputerBoth));
            boardChangedBegin(doCheckComputerMove, gameTreeChanged);
        }
        catch (GtpError e)
        {
            showError(e);
            clearStatus();
        }
    }

    private void computerNone()
    {
        m_computerBlack = false;
        m_computerWhite = false;
        m_menuBar.setComputerNone();
    }

    private boolean computerToMove()
    {
        if (m_board.getToMove() == GoColor.BLACK)
            return m_computerBlack;
        else
            return m_computerWhite;
    }

    private void computerWhite()
    {
        m_computerBlack = false;
        m_computerWhite = true;
        m_menuBar.setComputerWhite();
    }

    private Node createNode(Move move)
    {
        return Util.createNode(m_currentNode, move, m_clock);
    }

    private ContextMenu createContextMenu(GoPoint point)
    {
        ContextMenu.Listener listener = new ContextMenu.Listener()
            {
                public void clearAnalyze()
                {
                    GoGui.this.clearAnalyzeCommand();
                }

                public void editLabel(GoPoint point)
                {
                    GoGui.this.editLabel(point);
                }

                public void mark(GoPoint point, MarkType type,
                                 boolean mark)
                {
                    GoGui.this.mark(point, type, mark);
                }

                public void setAnalyzeCommand(AnalyzeCommand command)
                {
                    GoGui.this.setAnalyzeCommand(command, false, true, true);
                }
            };
        ArrayList supportedCommands = null;
        boolean noProgram = (m_gtp == null);
        if (! noProgram)
            supportedCommands = m_gtp.getSupportedCommands();
        return new ContextMenu(point, noProgram, supportedCommands,
                               m_programAnalyzeCommands,
                               m_guiBoard.getMark(point),
                               m_guiBoard.getMarkCircle(point),
                               m_guiBoard.getMarkSquare(point),
                               m_guiBoard.getMarkTriangle(point),
                               listener);
    }

    private void createThumbnail(File file)
    {
        if (! ThumbnailPlatform.checkThumbnailSupport())
            return;
        // Thumbnail creation does not work on GNU classpath 0.90 yet
        if (Platform.isGnuClasspath())
            return;
        String path = file.getAbsolutePath();
        if (! path.startsWith("/tmp") && ! path.startsWith("/var/tmp"))
            m_thumbnailCreator.create(file);
    }

    private void currentNodeChanged()
    {
        m_boardUpdater.update(m_gameTree, m_currentNode, m_board);
        updateFromGoBoard();
        if (m_gtp != null)
        {
            try
            {
                m_gtp.synchronize(m_board);
            }
            catch (GtpError e)
            {
                showError(e);
                checkProgramInSync();
            }
        }
    }

    private void detachProgram()
    {
        if (isCommandInProgress())
        {
            m_gtp.destroyGtp();
            m_gtp.close();
        }
        else
        {
            if (m_gtp != null && ! m_gtp.isProgramDead())
            {
                // Some programs do not handle closing the GTP stream
                // correctly, so we send a quit before
                try
                {
                    if (m_gtp.isCommandSupported("quit"))
                        m_gtp.send("quit");
                }
                catch (GtpError e)
                {
                }
                m_gtp.close();
            }
        }
        saveSession();
        if (m_analyzeCommand != null)
            clearAnalyzeCommand();
        m_gtp = null;
        m_name = null;
        m_version = null;
        m_toolBar.setComputerEnabled(false);
        m_menuBar.setComputerEnabled(false);
        m_gtpShell.dispose();
        m_gtpShell = null;
        if (m_analyzeDialog != null)
        {
            m_analyzeDialog.saveRecent();
            m_analyzeDialog.dispose();
            m_analyzeDialog = null;
        }
        resetBoard();
        clearStatus();
        setTitle();
    }

    private void editLabel(GoPoint point)
    {
        String value = m_currentNode.getLabel(point);
        value = JOptionPane.showInputDialog(this, "Label " + point, value);
        if (value == null)
            return;
        m_currentNode.setLabel(point, value);
        m_guiBoard.setLabel(point, value);
        setModified(true);
        updateGuiBoard();
    }

    private boolean endLengthyCommand()
    {
        clearStatus();
        m_menuBar.setNormalMode();
        m_toolBar.enableAll(true, m_currentNode);
        if (m_gtpShell != null)
            m_gtpShell.setCommandInProgess(false);
        if (m_analyzeCommand != null
            && (m_analyzeCommand.needsPointArg()
                || m_analyzeCommand.needsPointListArg()))
            setBoardCursor(Cursor.HAND_CURSOR);
        else
            setBoardCursorDefault();
        // Program could have been killed in cbInterrupt
        if (m_gtp == null)
            return false;
        GtpError error = m_gtp.getException();
        if (error != null)
        {
            showError(error);
            return false;
        }
        return true;
    }

    private boolean executeRoot()
    {
        setCurrentNode(m_gameTree.getRoot());
        if (m_gtp != null)
        {
            try
            {
                m_gtp.initSynchronize(m_board);
            }
            catch (GtpError error)
            {
                showError(error);
                return false;
            }
        }
        GameInformation gameInformation = m_gameTree.getGameInformation();
        setKomi(gameInformation.getKomi());
        setRules();
        setTimeSettings();
        currentNodeChanged();
        return ! isOutOfSync();
    }

    private void clearLoadedFile()
    {
        if (m_loadedFile == null)
            return;
        m_loadedFile = null;
        setTitle();
    }

    private void forward(int n)
    {
        assert(n >= 0);
        Node node = m_currentNode;
        for (int i = 0; i < n; ++i)
        {
            Node child = node.getChild();
            if (child == null)
                break;
            node = child;
        }
        gotoNode(node);
    }

    private void generateMove(boolean isSingleMove)
    {
        showStatus(m_name + " is thinking...");
        GoColor toMove = m_board.getToMove();
        String command;
        if (m_menuBar.getCleanup()
            && (m_gtp.isCommandSupported("kgs-genmove_cleanup")
                || m_gtp.isCommandSupported("genmove_cleanup")))
        {
            if (m_gtp.isCommandSupported("genmove_cleanup"))
                command = "genmove_cleanup";
            else
                command = "kgs-genmove_cleanup";
            if (toMove == GoColor.BLACK)
                command += " b";
            else if (toMove == GoColor.WHITE)
                command += " w";
            else
                assert(false);
        }
        else
        {
            command = m_gtp.getCommandGenmove(toMove);
            m_clock.startMove(toMove);
        }
        m_isSingleMove = isSingleMove;
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    computerMoved();
                }
            };
        runLengthyCommand(command, callback);
    }

    private int getRules()
    {
        return m_gameTree.getGameInformation().parseRules();
    }

    private void gotoNode(ConstNode node)
    {
        // GameTreeViewer is not disabled in score mode
        if (m_scoreMode)
            return;
        setCurrentNode(node);
        currentNodeChanged();
    }

    private void humanMoved(Move move)
    {
        GoPoint point = move.getPoint();
        if (point != null && m_board.getColor(point) != GoColor.EMPTY)
            return;
        m_clock.stopMove();
        if (point != null)
        {
            // Paint point immediately to pretend better responsiveness
            // because updating game tree or response to GTP play command
            // can be slow
            m_guiBoard.setColor(point, move.getColor());
            if (m_showLastMove)
                m_guiBoard.markLastMove(point);
            m_guiBoard.paintImmediately(point);
        }
        if (m_gtp != null && ! isOutOfSync())
        {
            try
            {
                m_gtp.updateHumanMove(m_board, move);
            }
            catch (GtpError e)
            {
                showError(e);
                boardChangedBegin(false, false);
                return;
            }
        }
        boolean newNodeCreated = false;
        ConstNode node = NodeUtil.getChildWithMove(m_currentNode, move);
        if (node == null)
        {
            newNodeCreated = true;
            node = createNode(move);
        }
        setCurrentNode(node);
        m_board.play(move);
        if (newNodeCreated)
            m_clock.startMove(m_board.getToMove());
        setModified(newNodeCreated);
        checkLostOnTime(move.getColor());
        m_resigned = false;
        boolean gameTreeChanged = newNodeCreated;
        if (newNodeCreated
            && m_currentNode.getFather().getNumberChildren() == 1)
        {
            if (m_gameTreeViewer != null)
                m_gameTreeViewer.addNewSingleChild(m_currentNode);
            gameTreeChanged = false;
        }
        boardChangedBegin(true, gameTreeChanged);
    }

    private void initGame(int size)
    {
        if (size != m_boardSize)
        {
            m_boardSize = size;
            m_board.init(size);
            m_guiBoard.initSize(size);
            m_guiBoard.setShowGrid(m_menuBar.getShowGrid());
            restoreMainWindow();
            if (m_gtpShell != null)
                restoreSize(m_gtpShell, "shell");
            if (m_analyzeDialog != null)
            {
                restoreSize(m_analyzeDialog, "analyze");
                m_analyzeDialog.setBoardSize(size);
            }
            if (m_gameTreeViewer != null)
                restoreSize(m_gameTreeViewer, "tree");
        }
        ArrayList handicap = m_board.getHandicapStones(m_handicap);
        if (handicap == null)
            showWarning("Handicap stone locations not\n" +
                        "defined for this board size");
        m_gameTree = new GameTree(size, m_prefs.getDouble("komi", 6.5),
                                  handicap, m_prefs.get("rules", ""),
                                  m_timeSettings);
        m_board.newGame();        
        setCurrentNode(m_gameTree.getRoot());
        updateFromGoBoard();
        resetBoard();
        m_clock.reset();
        m_lostOnTimeShown = false;
        setModified(false);
        m_resigned = false;
        m_menuBar.enableFindNext(false);
    }

    private void initialize()
    {
        if (m_file == null)
            newGame(m_boardSize);
        else
            newGameFile(m_boardSize, m_move);
        if (! m_prefs.getBoolean("show-info-panel", true))
        {
            m_menuBar.setShowInfoPanel(false);
            showInfoPanel();
        }
        if (m_prefs.getBoolean("show-toolbar", true))
        {
            m_menuBar.setShowToolbar(true);
            showToolbar();
        }
        restoreMainWindow();
        // Attaching a program can take some time, so we want to make
        // the window visible, but not draw the window content yet
        getLayeredPane().setVisible(false);
        setVisible(true);
        m_bookmarks = Bookmark.load();
        m_menuBar.setBookmarks(m_bookmarks);
        m_toolBar.enableAll(true, m_currentNode);
        if (m_program != null)
            attachProgram(m_program);
        setTitle();
        if (m_gtp == null
            || (! m_computerBlack && ! m_computerWhite))
            computerNone();
        else if (isComputerBoth())
            computerBoth();
        else  if (m_computerBlack)
            computerBlack();
        else
            computerWhite();
        updateGameInfo(true);
        registerSpecialMacHandler();
        // Children dialogs should be set visible after main window, otherwise
        // they get minimize window buttons and a taskbar entry (KDE 3.4)
        if (m_gtpShell != null && m_session.isVisible("shell"))
        {
            m_menuBar.setShowShell(true);
            cbShowShell();
        }
        if (m_session.isVisible("tree"))
        {
            m_menuBar.setShowTree(true);
            cbShowTree();
        }
        if (m_session.isVisible("analyze"))
        {
            m_menuBar.setShowAnalyze(true);
            cbAnalyze();
        }
        if (! m_initAnalyze.equals(""))
        {
            AnalyzeCommand analyzeCommand =
                AnalyzeCommand.get(this, m_initAnalyze);
            if (analyzeCommand == null)
                showError("Unknown analyze command \"" + m_initAnalyze
                          + "\"");
            else
                initAnalyzeCommand(analyzeCommand, true);
        }
        setTitleFromProgram();
        getLayeredPane().setVisible(true);
        toFrontLater();
        checkComputerMove();
    }

    private void initProgramAnalyzeCommands()
    {
        m_programAnalyzeCommands = null;
        if (m_gtp.isCommandSupported("gogui_analyze_commands"))
        {
            try
            {
                m_programAnalyzeCommands
                    = m_gtp.send("gogui_analyze_commands");
            }
            catch (GtpError e)
            {
            }    
        }
    }

    private void initScore(GoPoint[] isDeadStone)
    {
        resetBoard();
        GuiBoardUtil.scoreBegin(m_guiBoard, m_countScore, m_board,
                                isDeadStone);
        m_scoreMode = true;
        if (m_scoreDialog == null)
            m_scoreDialog = new ScoreDialog(this, this);
        double komi = m_gameTree.getGameInformation().getKomi();
        m_scoreDialog.showScore(m_countScore.getScore(komi, getRules()));
        m_scoreDialog.setVisible(true);
        m_menuBar.setScoreMode();
        showStatus("Please mark dead groups");
    }

    private void invokeAndWait(Runnable runnable)
    {
        try
        {
            SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException e)
        {
            System.err.println("Thread interrupted");
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            System.err.println("InvocationTargetException");
        }
    }

    private boolean isComputerBoth()
    {
        return (m_computerBlack && m_computerWhite);
    }

    private boolean isCommandInProgress()
    {
        if (m_gtp == null)
            return false;
        return m_gtp.isCommandInProgress();
    }

    private boolean isOutOfSync()
    {
        return (m_gtp != null && m_gtp.isOutOfSync());
    }

    private boolean loadFile(File file, int move)
    {
        try
        {
            FileInputStream in = new FileInputStream(file);
            LoadFileRunnable runnable = new LoadFileRunnable(in, file);
            if (file.length() > 500000)
            {
                newGame(m_boardSize); // Frees space if already large tree
                GuiUtil.runProgress(this, "Loading...", runnable);
            }
            else
                runnable.run(null);
            SgfReader reader = runnable.getReader();
            GameInformation gameInformation =
                reader.getGameTree().getGameInformation();
            initGame(gameInformation.getBoardSize());
            m_menuBar.addRecent(file);
            m_gameTree = reader.getGameTree();
            if (executeRoot() && move > 0)
                forward(move);            
            m_loadedFile = file;
            setTitle();
            String warnings = reader.getWarnings();
            if (warnings != null)
                showWarning("File " + file.getName() + ":\n" + warnings);
            SimpleDialogs.setLastFile(file);
            computerNone();
            createThumbnail(file);
        }
        catch (FileNotFoundException e)
        {
            showError("File not found:\n" + file);
            return false;
        }
        catch (SgfReader.SgfError e)
        {
            showError("Could not read file:", e);
            return false;
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            assert(false);
            return false;
        }
        return true;
    }

    public void mark(GoPoint point, MarkType type, boolean mark)
    {
        if (mark)
            m_currentNode.addMarked(point, type);
        else
            m_currentNode.removeMarked(point, type);
        if (type == MarkType.MARK)
            m_guiBoard.setMark(point, mark);
        else if (type == MarkType.CIRCLE)
            m_guiBoard.setMarkCircle(point, mark);
        else if (type == MarkType.SQUARE)
            m_guiBoard.setMarkSquare(point, mark);
        else if (type == MarkType.TRIANGLE)
            m_guiBoard.setMarkTriangle(point, mark);        
        setModified(true);
        updateGuiBoard();
    }

    private void newGame(int size)
    {
        initGame(size);
        executeRoot();
        updateGameInfo(true);
        updateFromGoBoard();
        m_toolBar.update(m_currentNode);
        updateMenuBar();
        m_menuBar.selectBoardSizeItem(m_board.getSize());
        setTitle();
        setTitleFromProgram();
        showToMove();
    }

    private void newGameFile(int size, int move)
    {
        initGame(size);
        loadFile(m_file, move);
        m_clock.reset();
        updateGameInfo(true);
        updateFromGoBoard();
        m_toolBar.update(m_currentNode);
        updateMenuBar();
        m_menuBar.selectBoardSizeItem(m_board.getSize());
    }

    private void registerSpecialMacHandler()
    {        
        if (! Platform.isMac())
            return;
        Platform.SpecialMacHandler handler = new Platform.SpecialMacHandler()
            {
                public boolean handleAbout()
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    cbAbout();
                    return true;
                }
                
                public boolean handleOpenFile(String filename)
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    if (! checkSaveGame())
                        return true;
                    loadFile(new File(filename), -1);
                    boardChangedBegin(false, true);
                    return true;
                }
                
                public boolean handleQuit()
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    close();
                    // close() calls System.exit() if not cancelled
                    return false;
                }
            };
        Platform.registerSpecialMacHandler(handler);
    }

    private void resetBoard()
    {
        clearStatus();
        m_guiBoard.clearAll();
        updateFromGoBoard();
        updateGuiBoard();
    }
    
    private void restoreMainWindow()
    {
        setState(Frame.NORMAL);
        m_session.restoreLocation(this, "main", m_boardSize);
        Dimension preferredCommentSize = null;
        String path = "windows/main/size-" + m_boardSize + "/fieldsize";
        int fieldSize = m_prefs.getInt(path, -1);
        if (fieldSize > 0)
            m_guiBoard.setPreferredFieldSize(new Dimension(fieldSize,
                                                           fieldSize));
        path = "windows/main/size-" + m_boardSize + "/comment";
        int width = m_prefs.getInt(path + "/width", -1);
        int height = m_prefs.getInt(path + "/height", -1);
        if (width > 0 && height > 0)
        {
            preferredCommentSize = new Dimension(width, height);
            m_comment.setPreferredSize(preferredCommentSize);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
        // To avoid smallish empty borders (less than one field size) on top
        // and bottom borders of the board we adjust the comment size slightly
        // if necessary
        if (m_infoPanel.getHeight() - m_guiBoard.getHeight() < 2 * fieldSize
            && preferredCommentSize != null && fieldSize > 0)
        {
            preferredCommentSize.height -= 2 * fieldSize;
            m_comment.setPreferredSize(preferredCommentSize);
            m_splitPane.resetToPreferredSizes();
            pack();
            }
    }

    private void restoreSize(Window window, String name)
    {
        m_session.restoreSize(window, name, m_boardSize);
    }

    private void runLengthyCommand(String cmd, Runnable callback)
    {
        assert(m_gtp != null);
        beginLengthyCommand();
        m_gtp.send(cmd, callback);
    }

    /** Save game to file.
        @return true If successfully saved.
    */
    private boolean save(File file)
    {
        OutputStream out;
        try
        {
            out = new FileOutputStream(file);
        }
        catch (FileNotFoundException e)
        {
            showError("Saving file failed", e);
            return false;
        }
        new SgfWriter(out, m_gameTree, "GoGui", Version.get());
        m_menuBar.addRecent(file);
        createThumbnail(file);
        m_loadedFile = file;
        setTitle();
        setModified(false);
        return true;
    }

    private boolean saveDialog()
    {
        File file = SimpleDialogs.showSaveSgf(this);
        if (file == null)
            return false;
        return save(file);
    }

    private void savePosition(File file) throws FileNotFoundException
    {
        OutputStream out = new FileOutputStream(file);
        new SgfWriter(out, m_board, "GoGui", Version.get());
        m_menuBar.addRecent(file);
    }

    private void saveSession()
    {
        Bookmark.save(m_bookmarks);
        if (m_gtpShell != null)
            m_gtpShell.saveHistory();
        if (m_analyzeDialog != null)
            m_analyzeDialog.saveRecent();
        m_session.saveLocation(this, "main", m_boardSize);
        if (m_help != null)
            saveSize(m_help, "help");
        saveSizeAndVisible(m_gameTreeViewer, "tree");
        if (m_gtp != null)
        {
            saveSizeAndVisible(m_gtpShell, "shell");
            saveSizeAndVisible(m_analyzeDialog, "analyze");
        }
        if (GuiUtil.isNormalSizeMode(this))
        {            
            String name = "windows/main/size-" + m_boardSize + "/fieldsize";
            m_prefs.putInt(name, m_guiBoard.getFieldSize().width);
            name = "windows/main/size-" + m_boardSize + "/comment/width";
            m_prefs.putInt(name, m_comment.getWidth());
            name = "windows/main/size-" + m_boardSize + "/comment/height";
            m_prefs.putInt(name, m_comment.getHeight());
        }
    }

    private void saveSize(JDialog dialog, String name)
    {
        m_session.saveSize(dialog, name, m_boardSize);
    }

    private void saveSizeAndVisible(JDialog dialog, String name)
    {
        m_session.saveSizeAndVisible(dialog, name, m_boardSize);
    }

    private void sendGtp(Reader reader)
    {
        if (m_gtp == null)
            return;
        java.io.BufferedReader in;
        in = new BufferedReader(reader);
        try
        {
            while (true)
            {
                try
                {
                    String line = in.readLine();
                    if (line == null)
                        break;
                    if (! m_gtpShell.send(line, this, true))
                        break;
                }
                catch (IOException e)
                {
                    showError("Error reading file");
                    break;
                }
            }
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException e)
            {
            }
        }
    }

    private void sendGtpFile(File file)
    {
        try
        {
            sendGtp(new FileReader(file));
        }
        catch (FileNotFoundException e)
        {
            showError("File not found: " + e.getMessage());
        }
    }

    private void sendGtpString(String commands)
    {        
        commands = commands.replaceAll("\\\\n", "\n");
        sendGtp(new StringReader(commands));
    }

    private void setBoardCursor(int type)
    {
        setCursor(m_guiBoard, type);
        setCursor(m_infoPanel, type);
    }

    private void setBoardCursorDefault()
    {
        setCursorDefault(m_guiBoard);
        setCursorDefault(m_infoPanel);
    }

    private void setCurrentNode(ConstNode node)
    {
        m_currentNode = m_gameTree.getNode(node);
    }

    private void setCursor(Component component, int type)
    {
        Cursor cursor = Cursor.getPredefinedCursor(type);
        component.setCursor(cursor);
    }

    private void setCursorDefault(Component component)
    {
        component.setCursor(Cursor.getDefaultCursor());
    }

    private void setKomi(double komi)
    {
        Util.sendKomi(this, komi, m_name, m_gtp);
    }

    private void setModified(boolean modified)
    {
        if (m_modified == modified)
            return;
        m_modified = modified;
        // Set Swing property on root window, good for e.g. Mac close
        // buttons (See Mac QA1146)
        getRootPane().putClientProperty("windowModified",
                                        Boolean.valueOf(modified));
        setTitle();
    }
    
    private void setResult(String result)
    {
        String oldResult = m_gameTree.getGameInformation().getResult();
        if (! (oldResult == null || oldResult.equals("")
               || oldResult.equals(result))
            && ! showQuestion("Overwrite old result " + oldResult + "\n" +
                              "with " + result + "?"))
            return;
        m_gameTree.getGameInformation().setResult(result);
    }

    private void setRules()
    {
        Util.sendRules(getRules(), m_gtp);
    }

    private void setTimeSettings()
    {
        if (m_gtp == null)
            return;
        TimeSettings timeSettings =
            m_gameTree.getGameInformation().getTimeSettings();
        if (timeSettings == null)
            return;
        if (! m_gtp.isCommandSupported("time_settings"))
            return;
        m_clock.setTimeSettings(timeSettings);
        String command = GtpUtil.getTimeSettingsCommand(timeSettings);
        try
        {
            m_gtp.send(command);
        }
        catch (GtpError e)
        {
            showError(e);
        }
    }

    private void setTitle()
    {
        if (m_titleFromProgram != null)
        {
            setTitle(m_titleFromProgram);
            return;
        }
        String appName = "GoGui";        
        if (m_gtp != null)
            appName = m_name;
        String filename = null;
        if (m_loadedFile != null)
        {
            filename = m_loadedFile.getName();
            if (m_modified)
                filename = filename + " [modified]";
        }
        else if (m_modified)
            filename = "[modified]";
        String gameName = m_gameTree.getGameInformation().suggestGameName();
        if (gameName != null)
        {
            if (filename != null)
                gameName = filename + "  " + gameName;
        }
        else if (filename != null)
            gameName = filename;
        if (gameName == null)
            setTitle(appName);        
        else
            setTitle(gameName + " - " + appName);
    }

    private void setTitleFromProgram()
    {
        m_titleFromProgram = null;
        if (m_gtp == null)
            return;
        if (m_gtp.isCommandSupported("gogui_title"))
        {
            try
            {
                m_titleFromProgram = m_gtp.send("gogui_title");
                setTitle(m_titleFromProgram);
            }
            catch (GtpError e)
            {
            }
        }
    }

    private void setupDone()
    {
        m_setupMode = false;
        m_showLastMove = m_menuBar.getShowLastMove();
        m_menuBar.setNormalMode();
        m_toolBar.enableAll(true, m_currentNode);
        int size = m_board.getSize();
        GoColor color[][] = new GoColor[size][size];
        for (int i = 0; i < m_board.getNumberPoints(); ++i)
        {
            GoPoint p = m_board.getPoint(i);
            color[p.getX()][p.getY()] = m_board.getColor(p);
        }
        GoColor toMove = m_board.getToMove();
        m_boardSize = size;
        m_board.newGame();        
        m_gameTree = new GameTree(size, m_prefs.getDouble("komi", 6.5), null,
                                  m_prefs.get("rules", ""), null);
        setCurrentNode(m_gameTree.getRoot());
        for (int i = 0; i < m_board.getNumberPoints(); ++i)
        {
            GoPoint point = m_board.getPoint(i);
            int x = point.getX();
            int y = point.getY();
            GoColor c = color[x][y];
            if (c == GoColor.BLACK)
                m_currentNode.addBlack(point);
            else if (c == GoColor.WHITE)
                m_currentNode.addWhite(point);
        }
        m_currentNode.setPlayer(toMove);
        executeRoot();
        clearLoadedFile();
        updateGameInfo(true);
        boardChangedBegin(false, false);
    }

    private void showError(String message, Exception e)
    {
        SimpleDialogs.showError(this, message, e);
    }

    private void showError(GtpError error)
    {        
        Util.showError(this, m_name, error);
    }

    private void showError(String message)
    {
        SimpleDialogs.showError(this, message);
    }

    private void showGameFinished()
    {
        if (m_gameFinishedMessage == null)
            m_gameFinishedMessage = new OptionalMessage(this);
        m_gameFinishedMessage.showMessage("Game finished");
    }

    private void showInfo(String message)
    {
        SimpleDialogs.showInfo(this, message);
    }

    private void showInfoPanel()
    {
        boolean showInfoPanel = m_menuBar.getShowInfoPanel();
        if (showInfoPanel == m_showInfoPanel)
            return;
        m_prefs.putBoolean("show-info-panel", showInfoPanel);
        m_showInfoPanel = showInfoPanel;
        if (showInfoPanel)
        {
            m_innerPanel.remove(m_guiBoard);
            m_splitPane.add(m_guiBoard);
            m_innerPanel.add(m_splitPane);
        }
        else
        {
            m_splitPane.remove(m_guiBoard);
            m_innerPanel.remove(m_splitPane);
            m_innerPanel.add(m_guiBoard);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
    }

    private boolean showQuestion(String message)
    {
        return SimpleDialogs.showQuestion(this, message);
    }

    private void showStatus(String text)
    {
        m_statusBar.setText(text);
    }

    private void showStatusSelectPointList()
    {
        showStatus("Select points for " + m_analyzeCommand.getLabel()
                   + " (last point with right button or modifier key down)");
    }

    private void showStatusSelectTarget()
    {
        showStatus("Select a target for "
                   + m_analyzeCommand.getResultTitle());
    }

    private void showToolbar()
    {
        boolean showToolbar = m_menuBar.getShowToolbar();
        if (showToolbar == m_showToolbar)
            return;
        m_prefs.putBoolean("show-toolbar", showToolbar);
        m_showToolbar = showToolbar;
        if (showToolbar)
        {
            getContentPane().add(m_toolBar, BorderLayout.NORTH);
            m_menuBar.setHeaderStyleSingle(false);
        }
        else
        {
            getContentPane().remove(m_toolBar);
            m_menuBar.setHeaderStyleSingle(true);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
    }

    private void showToMove()
    {
        if (m_showInfoPanel)
        {
            clearStatus();
            return;
        }
        GoColor toMove = m_board.getToMove();
        if (toMove == GoColor.WHITE)
            showStatus("White to play");
        else if (toMove == GoColor.BLACK)
            showStatus("Black to play");
    }

    private void showWarning(String message)
    {
        SimpleDialogs.showWarning(this, message);
    }

    private void toFrontLater()
    {
        // Calling toFront() directly does not give the focus to this
        // frame, if dialogs are open
        SwingUtilities.invokeLater(new Runnable() {
                public void run()
                {
                    toFront();
                }
            });
    }

    private void updateFromGoBoard()
    {
        GuiBoardUtil.updateFromGoBoard(m_guiBoard, m_board, m_showLastMove);
        if (m_currentNode.getMove() == null)
            m_guiBoard.markLastMove(null);
    }

    private void updateGameInfo(boolean gameTreeChanged)
    {
        m_gameInfo.update(m_currentNode, m_board);
        updateGameTree(gameTreeChanged);
        m_comment.setNode(m_currentNode);
        updateGuiBoard();
        if (m_analyzeDialog != null)
            m_analyzeDialog.setSelectedColor(m_board.getToMove());
    }

    private void updateGameTree(boolean gameTreeChanged)
    {
        if (m_gameTreeViewer == null)
            return;
        if (! gameTreeChanged)
        {
            m_gameTreeViewer.update(m_currentNode);
            return;
        }
        m_gameTreeViewer.update(m_gameTree, m_currentNode);
    }

    private void updateGuiBoard()
    {
        if (m_showVariations)
        {
            ArrayList childrenMoves
                = NodeUtil.getChildrenMoves(m_currentNode);
            GuiBoardUtil.showChildrenMoves(m_guiBoard, childrenMoves);
        }
        GuiBoardUtil.showMarkup(m_guiBoard, m_currentNode);
    }

    private void updateMenuBar()
    {
        m_menuBar.update(m_gameTree, m_currentNode, m_clock);
    }
}

