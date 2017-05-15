
public class ShotEntity extends Entity {
	
	/** The vertical speed at which the players shot moves */
	private double moveSpeed = -200;
	
	private Game game;
	/** True if this shot has been "used", i.e. its hit something */
	private boolean used = false;
	
	public ShotEntity(Game game, String ref, int x, int y) {
		super(ref, x, y);
		// TODO Auto-generated constructor stub
		this.game = game;
		dy = moveSpeed;
	}
	
	
	@Override
	public void collidedWith(Entity other) {
		// prevents double kills, if we've already hit something,
		// don't collide
//		if (used) {
//			return;
//		}
		// if we've hit an alien, kill it!
		if (other instanceof AlienEntity) {
			// remove the affected entities
//			game.removeEntity(this);
			game.removeEntity(other);
			
			// notify the game that the alien has been killed
//			game.notifyAlienKilled();
			used = true;
		}
	}
	
	public void move(long delta) {
		// proceed with normal move
		super.move(delta);
		
		// if shot off the screen, remove it
		if (y < -50) {
			game.removeEntity(this);
		}
	}
	
	
	
}