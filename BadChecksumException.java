package SpriteManipulator;

public class BadChecksumException extends Exception {
	private static final long serialVersionUID = -2535258398264906366L;

	public BadChecksumException(String message) {
		super(message);
	}

	public BadChecksumException() {}
}