//----------------------------------------------------------------------------
// RecentFileMenu.java
//----------------------------------------------------------------------------

package net.sf.gogui.gui;

import java.io.File;
import java.util.ArrayList;
import javax.swing.JMenu;

/** Menu for recent files.
    Automatically assigns short, but unique labels.
*/
public class RecentFileMenu
{
    /** Callback for events generated by RecentFileMenu. */
    public interface Listener
    {
        void fileSelected(String label, File file);
    }

    public RecentFileMenu(String label, String path, Listener listener)
    {
        assert listener != null;
        m_listener = listener;
        RecentMenu.Listener recentListener = new RecentMenu.Listener()
        {
            public void itemSelected(String label, String value)
            {
                m_listener.fileSelected(label, new File(value));
            }
        };
        m_menu = new RecentMenu(label, path, recentListener);
        for (int i = 0; i < m_menu.getCount(); ++i)
            if (! getFile(i).exists())
                m_menu.remove(i);
    }

    public void add(File file)
    {
        String name = file.getName();
        m_menu.add(name, file.toString());
        m_sameName.clear();
        for (int i = 0; i < getCount(); ++i)
            if (getName(i).equals(name))
                m_sameName.add(getValue(i));
        if (m_sameName.size() > 1)
        {
            int n = 0;
            while (true)
            {
                boolean samePrefix = true;
                if (file.toString().length() <= n)
                    break;
                char c = file.toString().charAt(n);
                for (int i = 0; i < m_sameName.size(); ++i)
                {
                    String sameName = m_sameName.get(i);
                    if (sameName.length() <= n || sameName.charAt(n) != c)
                    {
                        samePrefix = false;
                        break;
                    }
                }
                if (! samePrefix)
                    break;
                ++n;
            }
            for (int i = 0; i < getCount(); ++i)
                if (getName(i).equals(name))
                    m_menu.setLabel(i, getValue(i).substring(n));
        }
    }

    /** Don't modify the items in this menu! */
    public JMenu getMenu()
    {
        return m_menu.getMenu();
    }

    /** Set menu enabled if not empty, disabled otherwise. */
    public void updateEnabled()
    {
        m_menu.updateEnabled();
    }

    private final Listener m_listener;

    private final RecentMenu m_menu;

    private final ArrayList<String> m_sameName = new ArrayList<String>();

    private int getCount()
    {
        return m_menu.getCount();
    }

    private File getFile(int i)
    {
        return new File(getValue(i));
    }

    private String getValue(int i)
    {
        return m_menu.getValue(i);
    }

    private String getName(int i)
    {
        return getFile(i).getName();
    }
}
