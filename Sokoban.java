// This program is copyright VUW.
// You are granted permission to use it to construct your answer to a COMP103 assignment.
// You may not distribute it in any other way without permission.

/* Code for COMP 103, Assignment 3
 * Name: Adam Waring  
 * Usercode: Wareinadam
 * ID: 300 337 630
 */

import ecs100.*;
import java.util.*;
import java.io.*;

/** 
 * Sokoban
 */

public class Sokoban {

    private Square[][] squares;  // the array representing the warehouse
    private int rows;                  // the height of the warehouse
    private int cols;                   // the width of the warehouse

    private Coord workerPosition;     // the position of the worker
    private String workerDirection = "left"; // the direction the worker is facing

    Stack<ActionRecord> history;
    Stack<ActionRecord> redo;

    private final int maxLevels = 4; // maximum number of levels defined
    private int level = 0;                // current level 

    private Map<Character, String> fileCharacterToSquareType;  // character in level file -> square object
    private Map<String, String> directionToWorkerImage;                // worker direction ->  image of worker
    private Map<String, String> keyToAction;                                  // key string -> action to perform

    // Constructors
    /** 
     *  Constructs a new Sokoban object
     *  and set up the GUI.
     */
    public Sokoban() {
        UI.addButton("New Level", () -> {level = (level+1)%maxLevels; load(level);});
        UI.addButton("Restart", () -> {load(level);});
        UI.addButton("Undo", () -> undo());
        UI.addButton("Redo", () -> redo());
        UI.addButton("Left", () -> doAction("left"));
        UI.addButton("Up", () -> doAction("up"));
        UI.addButton("Down", () -> doAction("down"));
        UI.addButton("Right", () -> doAction("right"));

        UI.println("Push the boxes\n to their target postions.");
        UI.println("You may use keys (wasd or ijkl and u)");
        UI.setKeyListener(this::doKey);
        history = new Stack<ActionRecord>();
        redo = new Stack<ActionRecord>();

        initialiseMappings();
        load(0); // start with level zero
    }

    /** Responds to key actions */
    public void doKey(String key) { //calls undo, redo or doAction using a key press
        if(key.equals("u")){undo(); return; }       if(key.equals("u")){undo(); return;} //undo
        if(key.equals("r")){redo(); return; }       if(key.equals("R")){redo(); return;} //redo
        doAction(keyToAction.get(key));
    }

    public void redo(){ //redo
        if(redo.size() == 0){
            UI.println("You can't redo anymore moves right now");
            return;}

        ActionRecord lastAction = redo.pop(); //remove from last action
        history.push(lastAction); // add to history
        String direction = lastAction.direction(); //set last actions direction

        if(!lastAction.isMove()){ //if last action not move, then call push
            push(direction);
        }   
        else if(lastAction.isMove()){ //if last action move, then call move
            move(direction);
        }
        draw();
    }    

    public void undo(){ //undo action
        if(history.size() == 0){
            UI.println("You have un-done every action");
            return;}

        ActionRecord lastAction = history.pop(); //remove from last action
        redo.push(lastAction); //add to redo
        String direction = lastAction.direction(); //set last actions direction

        if(lastAction.isPush()){ //if last action push, then call pull & move
            pull(oppositeDirection(direction));
            move(oppositeDirection(direction));
        }   
        else if(lastAction.isMove()){ //if last action move, then call move
            move(oppositeDirection(direction));
        }
        draw();
    }

    /** 
     *  Moves the worker in the specified direction, if possible.
     *  If there is box in front of the Worker and a space in front of the box,
     *  then push the box.
     *  Otherwise, if there is anything in front of the Worker, do nothing.
     * @param action the action to perform 
     */
    public void doAction(String action) {
        if (action==null) 
            return;
        while (!redo.isEmpty()){redo.pop();} //used to clear redo once another move is made, else will cause unexpected errors.
        
        workerDirection = action; // action can only be a move; record it.
        Coord nextP = workerPosition.next(workerDirection);  // where the worker would move to
        Coord nextNextP = nextP.next(workerDirection);         // where the worker would move to in two steps

        if ( squares[nextP.row][nextP.col].hasBox() && squares[nextNextP.row][nextNextP.col].isFree() ) {
            push(workerDirection);
            history.push(new ActionRecord("push", action));
            if (isSolved()) {
                UI.println("\n*** YOU WIN! ***\n");
                // flicker with the boxes to indicate win
                for (int i=0; i<12; i++) {
                    for (int row=0; row<rows; row++)
                        for (int column=0; column<cols; column++) {
                            Square square=squares[row][column];

                            // toggle shelf squares
                            if (square.hasBox()) {square.moveBoxOff(); drawSquare(row, column);}
                            else if (square.isEmptyShelf()) {square.moveBoxOn(); drawSquare(row, column);}
                        }
                    UI.sleep(100);
                }
            }
        }
        else if ( squares[nextP.row][nextP.col].isFree() ) { // can the worker move?
            move(workerDirection);
            history.push(new ActionRecord("move", action));
        }
    }

    /** Moves the worker into the new position (guaranteed to be empty) 
     * @param direction the direction the worker is heading
     */
    public void move(String direction) {
        drawSquare(workerPosition); // display square under worker
        workerPosition = workerPosition.next(direction); // new worker position
        drawWorker();  // display worker at new position
        Trace.println("Move " + direction);
    }

    /** Push: Moves the Worker, pushing the box one step 
     *  @param direction the direction the worker is heading
     */
    public void push(String direction) {
        drawSquare(workerPosition); // display square under worker
        workerPosition = workerPosition.next(direction); // new worker position
        drawWorker();  // display worker at new position

        Coord boxP = workerPosition.next(direction); // this is two steps from the original worker position
        squares[workerPosition.row][workerPosition.col].moveBoxOff(); // remove box from its current position
        squares[boxP.row][boxP.col].moveBoxOn(); // place box on its new position
        drawSquare(boxP);
        draw();

        Trace.println("Push " + direction);
    }

    /** Pull: (useful for undoing a push in the opposite direction)
     *  move the Worker in direction from direction,
     *  pulling the box into the Worker's old position
     */
    public void pull(String direction) {
        String oppositeDir = oppositeDirection(direction);
        Coord boxP = workerPosition.next(oppositeDir);

        squares[boxP.row][boxP.col].moveBoxOff();
        squares[workerPosition.row][workerPosition.col].moveBoxOn();

        drawSquare(boxP);
        drawSquare(workerPosition);

        workerDirection = oppositeDir;
        drawWorker();

        Trace.println("Pull " + direction);
    }

    /** Load a grid of squares (and Worker position) from a file */
    public void load(int level) {
        while(!history.isEmpty()) //clear history stack when starting a new level 
            history.pop();
        while(!redo.isEmpty()) //clear redo stack when starting a new level 
            redo.pop();   
        File f = new File("warehouse" + level + ".txt");

        if (f.exists()) {
            List<String> lines = new ArrayList<String>();

            try {
                Scanner sc = new Scanner(f);

                while (sc.hasNext())
                    lines.add(sc.nextLine());

                sc.close();
            } catch(IOException e) {
                Trace.println("File error: " + e);
            }

            rows = lines.size();
            cols = lines.get(0).length();

            squares = new Square[rows][cols];

            for(int row = 0; row < rows; row++) {
                String line = lines.get(row);

                for(int col = 0; col < cols; col++) {

                    if (col>=line.length())
                        squares[row][col] = new Square("empty");
                    else {
                        char ch = line.charAt(col);

                        if ( fileCharacterToSquareType.containsKey(ch) )
                            squares[row][col] = new Square(fileCharacterToSquareType.get(ch));
                        else {
                            squares[row][col] = new Square("empty");
                            UI.printf("Invalid char: (%d, %d) = %c \n", row, col, ch);
                        }

                        if (ch=='A')
                            workerPosition = new Coord(row,col);
                    }
                }
            }
            draw();

        }
    }

    // Drawing 

    private static final int leftMargin = 40;
    private static final int topMargin = 40;
    private static final int squareSize = 25;

    /** Draw the grid of squares on the screen, and the Worker */
    public void draw() {
        UI.clearGraphics();
        // draw squares
        for(int row = 0; row<rows; row++)
            for(int col = 0; col<cols; col++)
                drawSquare(row, col);

        drawWorker();
    }

    private void drawWorker() {
        UI.drawImage(directionToWorkerImage.get(workerDirection),
            leftMargin+(squareSize* workerPosition.col),
            topMargin+(squareSize* workerPosition.row),
            squareSize, squareSize);
    }

    private void drawSquare(Coord pos) {
        drawSquare(pos.row, pos.col);
    }

    private void drawSquare(int row, int col) {
        String imageName = squares[row][col].imageName();

        if (imageName != ".gif")
            UI.drawImage(imageName,
                leftMargin+(squareSize* col),
                topMargin+(squareSize* row),
                squareSize, squareSize);
    }

    /** 
     *  @returns true, if the warehouse is solved, i.e.,  
     *  all the shelves have boxes on them 
     */
    public boolean isSolved() {
        for(int row = 0; row<rows; row++) {
            for(int col = 0; col<cols; col++)
                if(squares[row][col].isEmptyShelf())
                    return  false;
        }

        return true;
    }

    /** 
     * @return the direction that is opposite of the parameter 
     */
    public String oppositeDirection(String direction) {
        if ( direction.equalsIgnoreCase("right")) return "left";
        if ( direction.equalsIgnoreCase("left"))  return "right";
        if ( direction.equalsIgnoreCase("up"))    return "down";
        if ( direction.equalsIgnoreCase("down"))  return "up";
        return direction;
    }

    private void initialiseMappings() {
        // character in level file -> square type
        fileCharacterToSquareType = new HashMap<Character, String>();
        fileCharacterToSquareType.put('.',  "empty");
        fileCharacterToSquareType.put('A', "empty");  // initial position of worker is an empty square beneath
        fileCharacterToSquareType.put('#',  "wall");
        fileCharacterToSquareType.put('S', "emptyShelf");
        fileCharacterToSquareType.put('B',  "box");

        // worker direction ->  image of worker
        directionToWorkerImage = new HashMap<String, String>();
        directionToWorkerImage.put("up", "worker-up.gif");
        directionToWorkerImage.put("down", "worker-down.gif");
        directionToWorkerImage.put("left", "worker-left.gif");
        directionToWorkerImage.put("right", "worker-right.gif");

        // key string -> action to perform
        keyToAction = new HashMap<String,String>();
        keyToAction.put("i", "up");     keyToAction.put("I", "up");   
        keyToAction.put("k", "down");   keyToAction.put("K", "down"); 
        keyToAction.put("j", "left");   keyToAction.put("J", "left"); 
        keyToAction.put("l", "right");  keyToAction.put("L", "right");

        keyToAction.put("w", "up");     keyToAction.put("W", "up");   
        keyToAction.put("s", "down");   keyToAction.put("S", "down"); 
        keyToAction.put("a", "left");   keyToAction.put("A", "left"); 
        keyToAction.put("d", "right");  keyToAction.put("D", "right");
        keyToAction.put("u", "undo");  keyToAction.put("U", "undo");
        keyToAction.put("r", "redo");  keyToAction.put("R", "redo");
    }

    public static void main(String[] args) {
        new Sokoban();
    }
}
