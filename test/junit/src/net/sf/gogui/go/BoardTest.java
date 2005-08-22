//----------------------------------------------------------------------------
// $Id$
// $Source$
//----------------------------------------------------------------------------

package net.sf.gogui.go;

//----------------------------------------------------------------------------

public class BoardTest
    extends junit.framework.TestCase
{
    public static void main(String args[])
    {
        junit.textui.TestRunner.run(suite());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(BoardTest.class);
    }

    public void testCapture()
    {
        Board board = new Board(19);
        board.play(GoPoint.create(0, 0), GoColor.BLACK);
        board.play(GoPoint.create(1, 0), GoColor.BLACK);
        board.play(GoPoint.create(0, 1), GoColor.WHITE);
        board.play(GoPoint.create(1, 1), GoColor.WHITE);
        board.play(GoPoint.create(2, 0), GoColor.WHITE);
        assertEquals(GoColor.EMPTY, board.getColor(GoPoint.create(0, 0)));
        assertEquals(GoColor.EMPTY, board.getColor(GoPoint.create(1, 0)));
        assertEquals(GoColor.WHITE, board.getColor(GoPoint.create(0, 1)));
        assertEquals(GoColor.WHITE, board.getColor(GoPoint.create(1, 1)));
        assertEquals(GoColor.WHITE, board.getColor(GoPoint.create(2, 0)));
        assertEquals(2, board.getCapturedB());
        assertEquals(0, board.getCapturedW());
    }
}

//----------------------------------------------------------------------------