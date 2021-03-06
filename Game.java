

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * The main hook of our game. This class with both act as a manager
 * for the display and central mediator for the game logic. 
 * 
 * Display management will consist of a loop that cycles round all
 * entities in the game asking them to move and then drawing them
 * in the appropriate place. With the help of an inner class it
 * will also allow the player to control the main ship.
 * 
 * As a mediator it will be informed when entities within our game
 * detect events (e.g. alien killed, played died) and will take
 * appropriate game actions
 */
public class Game extends Canvas {
	/** The stragey that allows us to use accelerate page flipping */
	private BufferStrategy strategy;
	/** True if the game is currently "running", i.e. the game loop is looping */
	private boolean gameRunning = true;
	/** The list of all the entities that exist in our game */
	private ArrayList entities = new ArrayList();
	/** The list of entities that need to be removed from the game this loop */
	private ArrayList removeList = new ArrayList();
	/** The entity representing the player */
	private ShipEntity ship;
	/** The speed at which the player's ship should move (pixels/sec) */
	private double moveSpeed = 100;
	/** The time at which last fired a shot */
	private long lastFire = 0;
	/** The interval between our players shot (ms) */
	private long firingInterval = 150;
	/** The number of aliens left on the screen */
	private int alienCount;
	/** The time at which ammo was spawned */
	protected long lastAmmoSpawn = 0;
	/** The interval between ammo spawns */
	private long ammoInterval = 8*1000;
	
	/** The message to display which waiting for a key press */
	private String message = "";
	/** True if we're holding up game play until a key has been pressed */
	private boolean waitingForKeyPress = true;
	/** True if the left cursor key is currently pressed */
	private boolean leftPressed = false;
	/** True if the right cursor key is currently pressed */
	private boolean rightPressed = false;
	/** True if the up cursor key is currently pressed */
	private boolean upPressed = false;
	/** True if the down cursor key is currently pressed */
	private boolean downPressed = false;
	/** True if we are firing */
	private boolean firePressed = false;
	/** True if game logic needs to be applied this loop, normally as a result of a game event */
	private boolean logicRequiredThisLoop = false;
	
	/** True if ammo has been taken */
	protected boolean hasTakenAmmo = true;
	
	private SerialTest serialReader;
	/**
	 * Construct our game and set it running.
	 */
	public Game() {
		// create a frame to contain our game
		JFrame container = new JFrame("Space Invaders ECE3420");
		
		// get hold the content of the frame and set up the resolution of the game
		JPanel panel = (JPanel) container.getContentPane();
		panel.setPreferredSize(new Dimension(800,600));
		panel.setLayout(null);
		
		// setup our canvas size and put it into the content of the frame
		setBounds(0,0,800,600);
		panel.add(this);
		
		// Don't let AWT repaint canvas because we will do that ourselves
		setIgnoreRepaint(true);
		
		// finally make the window visible 
		container.pack();
		container.setResizable(false);
		container.setVisible(true);
		
		container.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		
		// add a key input system (defined below) to our canvas
		// so we can respond to key pressed
		addKeyListener(new KeyInputHandler());
		
		// request the focus so key events come to us
		requestFocus();

		// create the buffering strategy which will allow AWT
		// to manage our accelerated graphics
		createBufferStrategy(2);
		strategy = getBufferStrategy();
		
		// initialise the entities in our game so there's something
		// to see at startup
		initEntities();

	}
	
	/**
	 * Start a fresh game, this should clear out any old data and
	 * create a new set.
	 */
	private void startGame() {
		// clear out any existing entities and intialize a new set
		entities.clear();
		initEntities();
		
		// blank out any keyboard settings we might currently have
		leftPressed = false;
		rightPressed = false;
		firePressed = false;
		upPressed = false;
		downPressed = false;
	}
	
	/**
	 * Initialize the starting state of the entities (ship and aliens). Each
	 * entity will be added to the overall list of entities in the game.
	 */
	private void initEntities() {
		// create the player ship and place it roughly in the center of the screen
		ship = new ShipEntity(this,"sprites/ship.gif",370,550);
		entities.add(ship);
		
		// create a block of aliens (5 rows, by 12 aliens, spaced evenly)
		alienCount = 0;
		for (int row=0;row<5;row++) {
			for (int x=0;x<10;x++) {
				Entity alien = new AlienEntity(this,"sprites/suh1.gif",100+(x*50),(50)+row*30);
				entities.add(alien);
				alienCount++;
			}
		}
		
		hasTakenAmmo = true;
	}
	
	/**
	 * Notification from a game entity that the logic of the game
	 * should be run at the next opportunity (normally as a result of some
	 * game event)
	 */
	public void updateLogic() {
		logicRequiredThisLoop = true;
	}
	
	/**
	 * Remove an entity from the game. 
	 * 
	 * @param entity The entity that should be removed
	 */
	public void removeEntity(Entity entity) {
		removeList.add(entity);
	}
	
	/**
	 * Notification that the player has died. 
	 */
	public void notifyDeath() {
		message = "Oh no! YOU DIED HAHA. Try again?";
		waitingForKeyPress = true;
	}
	
	/**
	 * Notification that the player has won since all the aliens
	 * are dead.
	 */
	public void notifyWin() {
		message = "Good job. You survived";
		waitingForKeyPress = true;
	}
	
	/**
	 * Notification that an alien has been killed
	 */
	public void notifyAlienKilled() {
		// reduce the alien count, if there are none left, the player has won!
				alienCount--;
				
				if (alienCount == 0) {
					notifyWin();
				}
				// if there are still some aliens left then speed them up
				for (int i=0;i<entities.size();i++) {
					Entity entity = (Entity) entities.get(i);
					
					if (entity instanceof AlienEntity) {
						// speed up by 2%
						entity.setHorizontalMovement(entity.getHorizontalMovement() * 1.02);
						entity.setVerticalMovement(entity.getVerticalMovement() * 1.02);
					}
				}
				
	}
	
	/**
	 * Attempt to fire a shot from the player. We must first check that the player can fire at this 
	 * point after waiting for an appropriate amount of time
	 */
	public void tryToFire() {
		// check that we have waiting long enough to fire
		ShipEntity curr = ((ShipEntity)(ship));
		if (System.currentTimeMillis() - lastFire < firingInterval || !curr.hasAmmo() ) {
			return;
		}
		
		// if we waited long enough, create the shot entity, and record the time.
		lastFire = System.currentTimeMillis();
		ShotEntity shot1 = new ShotEntity(this,"sprites/shot.gif",ship.getX()+10,ship.getY()-30, 0, -400);
		ShotEntity shot2 = new ShotEntity(this, "sprites/downshot.gif", ship.getX() +10, ship.getY()+30, 0, 400);
		ShotEntity shot3 = new ShotEntity(this, "sprites/rightshot.gif", ship.getX() + 20, ship.getY(), 400, 0);
		ShotEntity shot4 = new ShotEntity(this, "sprites/leftshot.gif", ship.getX() - 10, ship.getY(), -400, 0);
		entities.add(shot1);
		entities.add(shot2);
		entities.add(shot3);
		entities.add(shot4);
		
		curr.decreaseAmmo();
	}
	
	/**
	 * Attempt to spawn ammo
	 */
	public void tryToSpawnAmmo() {
		
		if(System.currentTimeMillis() - lastAmmoSpawn > ammoInterval && hasTakenAmmo && !waitingForKeyPress) {
			lastAmmoSpawn = System.currentTimeMillis();
			int spawnX = (int)(Math.random() * 760) + 20;
			int spawnY = (int)(Math.random() * 560) + 20;
			GiftEntity gift = new GiftEntity(this, "sprites/ammosmall.gif", spawnX, spawnY);
			entities.add(gift);
			hasTakenAmmo = false;
		}
		else {
			return;
		}
		
		
	}
	
	public void parseCommands(String s) {
		//System.out.println("parseCommand");
		//System.out.println("parsed: " + s);
		if(s == null) { return;}
		if(s.contains("A")){
			//System.out.println("upprss set to TRUE");
			upPressed = true;
			downPressed = false;
			leftPressed = false;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("B")){
			upPressed = true;
			downPressed = false;
			leftPressed = false;
			rightPressed = true;
			firePressed = false;
		}
		if(s.contains("C")){
			upPressed = false;
			downPressed = false;
			leftPressed = false;
			rightPressed = true;
			firePressed = false;
		}
		if(s.contains("D")){
			upPressed = false;
			downPressed = true;
			leftPressed = false;
			rightPressed = true;
			firePressed = false;
		}
		if(s.contains("E")){
			upPressed = false;
			downPressed = true;
			leftPressed = false;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("F")){
			upPressed = false;
			downPressed = true;
			leftPressed = true;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("G")){
			upPressed = false;
			downPressed = false;
			leftPressed = true;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("H")){
			upPressed = true;
			downPressed = false;
			leftPressed = true;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("O")){
			upPressed = false;
			downPressed = false;
			leftPressed = false;
			rightPressed = false;
			firePressed = false;
		}
		if(s.contains("L")){
			//System.out.println("upprss set to TRUE");
			upPressed = true;
			downPressed = false;
			leftPressed = false;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("M")){
			upPressed = true;
			downPressed = false;
			leftPressed = false;
			rightPressed = true;
			firePressed = true;
		}
		if(s.contains("N")){
			upPressed = false;
			downPressed = false;
			leftPressed = false;
			rightPressed = true;
			firePressed = true;
		}
		if(s.contains("P")){
			upPressed = false;
			downPressed = true;
			leftPressed = false;
			rightPressed = true;
			firePressed = true;
		}
		if(s.contains("Q")){
			upPressed = false;
			downPressed = true;
			leftPressed = false;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("R")){
			upPressed = false;
			downPressed = true;
			leftPressed = true;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("S")){
			upPressed = false;
			downPressed = false;
			leftPressed = true;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("T")){
			upPressed = true;
			downPressed = false;
			leftPressed = true;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("U")){
			upPressed = false;
			downPressed = false;
			leftPressed = false;
			rightPressed = false;
			firePressed = true;
		}
		if(s.contains("Z")){
			tryToSpawnAmmo();
		}
	

	}
	
	/**
	 * The main game loop. This loop is running during all game
	 * play as is responsible for the following activities:

	 * - Working out the speed of the game loop to update moves
	 * - Moving the game entities
	 * - Drawing the screen contents (entities, text)
	 * - Updating game events
	 * - Checking Input
	 */
	public void gameLoop() {
		long lastLoopTime = System.currentTimeMillis();
		serialReader = new SerialTest();
		new Thread(serialReader).start();
		try {
			Thread.sleep(200);
			System.out.println("hello world");
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		int updateCounter = 0;
		// keep looping round til the game ends
		while (gameRunning) {
			//DO STUFF
			// work out how long its been since the last update, this
			// will be used to calculate how far the entities should
			// move this loop
			long delta = System.currentTimeMillis() - lastLoopTime;
			lastLoopTime = System.currentTimeMillis();
			
			// Get hold of a graphics context for the accelerated 
			// surface and blank it out
			Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
			g.setColor(Color.black);
			g.fillRect(0,0,800,600);
			g.setColor(Color.WHITE);
			g.drawString("Ammo: "+ ship.getAmmo(), 25, 25);
			
			//System.out.println("Up pressed value is : " + upPressed);
			// loop through entites, asking each one to move itself
			if (!waitingForKeyPress) {
				for (int i=0;i<entities.size();i++) {
					Entity entity = (Entity) entities.get(i);
					
					entity.move(delta);
				}
			}
			
			
			//Draw all the entities
			for (int i=0;i<entities.size();i++) {
				Entity entity = (Entity) entities.get(i);
				
				entity.draw(g);
				
			}
			
			
			//Check for collisions. If there is a collision, notify both entities 
			for (int p=0;p<entities.size();p++) {
				for (int s=p+1;s<entities.size();s++) {
					Entity me = (Entity) entities.get(p);
					Entity him = (Entity) entities.get(s);
					
					if (me.collidesWith(him)) {
						me.collidedWith(him);
						him.collidedWith(me);
					}
				}
			}
			
			// remove any entity that has been marked for clear up
			entities.removeAll(removeList);
			removeList.clear();

			//If any entity requests that logic is required, then loop through them all and do their logic
			if (logicRequiredThisLoop) {
				for (int i=0;i<entities.size();i++) {
					Entity entity = (Entity) entities.get(i);
					entity.doLogic();
				}
				
				logicRequiredThisLoop = false;
			}
			
			// if we're waiting for an "any key" press then draw the current message 
			if (waitingForKeyPress) {
				g.setColor(Color.white);
				g.drawString(message,(800-g.getFontMetrics().stringWidth(message))/2,250);
				g.drawString("Press any key",(800-g.getFontMetrics().stringWidth("Press any key"))/2,300);
			}
			
			// finally, we've completed drawing so clear up the graphics
			// and flip the buffer over
			g.dispose();
			strategy.show();
			
			parseCommands(serialReader.getReading());
			//System.out.println("reading is " + serialReader.getReading());
			ship.setHorizontalMovement(0);
			ship.setVerticalMovement(0);
			
			if ((leftPressed) && (!rightPressed)) {
				ship.setHorizontalMovement(-moveSpeed);
			} else if ((rightPressed) && (!leftPressed)) {
				ship.setHorizontalMovement(moveSpeed);
			}
			
			if ((upPressed) && (!downPressed)) {
				ship.setVerticalMovement(-moveSpeed);
			} else if ((downPressed) && (!upPressed)) {
				ship.setVerticalMovement(moveSpeed);
			}
			
			
			// if we're pressing fire, attempt to fire
			if (firePressed) {
				tryToFire();
			}
			//tryToSpawnAmmo();
			//System.out.println(serialReader.getReading());
			
			
			
			//try {Thread.sleep(100);} catch (Exception e) {}
			
			
			

			try { Thread.sleep(10); } catch (Exception e) {}
		}
	}
	
	/**
	 * A class to handle keyboard input from the user.
	 *
	 */
	private class KeyInputHandler extends KeyAdapter {
		/** The number of key presses we've had while waiting for some keypress */
private int pressCount = 1;
		
		/**
		 * Notification from AWT that a key has been pressed. Note that
		 * a key being pressed is equal to being pushed down but *NOT*
		 * released. Thats where keyTyped() comes in.
		 *
		 * @param e The details of the key that was pressed 
		 */
		
		/**
		 * Notification from AWT that a key has been released.
		 *
		 * @param e The details of the key that was released 
		 */
		


		/**
		 * Notification from AWT that a key has been typed. Note that
		 * typing a key means to both press and then release it.
		 *
		 * @param e The details of the key that was typed. 
		 */
		public void keyTyped(KeyEvent e) {
			// if we're waiting for a "any key" type then
			// check if we've recieved any recently. We may
			// have had a keyType() event from the user releasing
			// the shoot or move keys, hence the use of the "pressCount"
			// counter.
			if (waitingForKeyPress) {
				if (pressCount == 1) {
					// since we've now received our key typed
					// event we can mark it as such and start 
					// our new game
					waitingForKeyPress = false;
					startGame();
					pressCount = 0;
				} else {
					pressCount++;
				}
			}
			
			// if we hit escape, then quit the game
			if (e.getKeyChar() == 27) {
				System.exit(0);
			}
		}
	}
			
	
	/**
	 * Entry point of game
	 */
	public static void main(String argv[]) {
		Game g =new Game();

		// Start the main game loop, note: this method will not
		// return until the game has finished running. Hence we are
		// using the actual main thread to run the game.
		g.gameLoop();
	}
}

