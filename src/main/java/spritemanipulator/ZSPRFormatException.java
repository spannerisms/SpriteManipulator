package spritemanipulator;

/**
 * A generic exception for problems specific to sprite files.
 * 
 * @author fatmanspanda
 */
public class ZSPRFormatException extends Exception {
	private static final long serialVersionUID = 108159263121372582L;

	public ZSPRFormatException(String message) {
		super(message);
	}

	private ZSPRFormatException() {}
}