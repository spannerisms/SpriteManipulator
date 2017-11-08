package SpriteManipulator;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Wrapper class for all data related to SPR files
 * @author CR = SpriteManipulator.CR;
 *
 */
public class SPRFile {
	// class constants
	public static final byte[] FLAG = SpriteManipulator.FLAG;
	public static final byte[] ZSPR_VERSION = SpriteManipulator.ZSPR_VERSION;
	public static final String SZPR_VERSION_TAG = SpriteManipulator.SZPR_VERSION_TAG;
	public static final String ZSPR_SPEC = SpriteManipulator.ZSPR_SPEC;
	public static final int[] CKSM_OFFSET_INDICES = SpriteManipulator.CKSM_OFFSET_INDICES;
	public static final int[] SPRITE_OFFSET_INDICES = SpriteManipulator.SPRITE_OFFSET_INDICES;
	public static final int[] PAL_OFFSET_INDICES = SpriteManipulator.PAL_OFFSET_INDICES;
	public static final int SPRITE_NAME_OFFSET =  SpriteManipulator.SPRITE_NAME_OFFSET;

	// class constants
	// data sizes for sprites
	public static final int SPRITE_DATA_SIZE = SpriteManipulator.SPRITE_DATA_SIZE;
	private static final short SPRITE_SIZE_SHORT = (short) SPRITE_DATA_SIZE; // cast to not get extra bytes
	public static final int PAL_DATA_SIZE = SpriteManipulator.PAL_DATA_SIZE;
	private static final short PAL_SIZE_SHORT = (short) SPRITE_DATA_SIZE; // cast to not get extra bytes

	// local vars
	private byte[] spriteData;
	private byte[] palData;
	private byte[] glovesData;
	private String spriteName;
	private String authorName;
	private byte[] dataStream;

	// default constructor
	public SPRFile() {
		// default empty
	}

	// constructor
	public SPRFile(byte[] spriteData, byte[] palData, byte[] glovesData,
			String spriteName, String authorName) {
		this.spriteData = spriteData;
		this.palData = palData;
		this.glovesData = glovesData;
		this.spriteName = spriteName;
		this.authorName = authorName;
		refreshDataStream();
	}

	// no sprite name or author name
	public SPRFile(byte[] spriteData, byte[] palData, byte[] glovesData) {
		this(glovesData, glovesData, glovesData, null, "");
	}

	public void setSpriteData(byte[] spriteData) {
		this.spriteData = spriteData;
	}

	public byte[] getSpriteData() {
		return this.spriteData;
	}

	public void setPalData(byte[] palData) {
		this.palData = palData;
	}

	public byte[] getPalData() {
		return this.palData;
	}

	public void setGlovesData(byte[] glovesData) {
		this.glovesData = glovesData;
	}

	public byte[] getGlovesData() {
		return this.glovesData;
	}

	public void setSpriteName(String spriteName) {
		this.spriteName = spriteName;
	}

	public String getSpriteName() {
		return this.spriteName;
	}

	public void setAuthorName(String authorName) {
		this.authorName = authorName;
	}

	public String getAuthorName() {
		return this.authorName;
	}

	public void setNameFromPath(String path) {
		// find file name from path
		int dl = path.lastIndexOf('.');
		int sl = path.lastIndexOf('/');
		String sprName;
		
		if (sl == -1) { // if not full path, use just up until extension
			sprName = path.substring(0, dl);
		} else { // if longer path, find what's between '/' and '.'
			sprName = path.substring(sl + 1, dl);
		}

		this.spriteName = sprName;
	}

	/**
	 * gets data stream
	 */
	public byte[] getDataStream() {
		return dataStream;
	}

	/**
	 * Creates a data stream for this sprite
	 */
	public void refreshDataStream() {
		ArrayList<Byte> ret = new ArrayList<Byte>();

		// add header
		for (byte b : FLAG) { // 4 bytes
			ret.add(b);
		}

		// add version
		for (byte b : ZSPR_VERSION) { // 1 byte
			ret.add(b);
		}

		// add checksum - null data for now
		for (byte b : new byte[2]) { // 2 bytes
			ret.add(b);
		}

		// add sprite data offset, start with 0s
		for (byte b : new byte[4]) { // 4 bytes
			ret.add(b);
		}

		// add sprite size (constant)
		for (byte b : toByteArray(SPRITE_SIZE_SHORT)) { // 2 bytes
			ret.add(b);
		}

		// add palette data offset, start with 0s
		for (byte b : new byte[4]) { // 4 bytes
			ret.add(b);
		}

		// add palette size (constant)
		for (byte b : toByteArray(PAL_SIZE_SHORT)) { // 2 bytes
			ret.add(b);
		}

		// add reserved (constant size)
		for (byte b : new byte[8]) { // 8 bytes
			ret.add(b);
		}

		// convert to byte arrays
		byte[] sName = toByteArray(spriteName + "\0"); // add null terminators here
		byte[] auth = toByteArray(authorName + "\0");

		// add sprite name
		for (byte b : sName) { // variable length; null terminated
			ret.add(b);
		}

		// add author name
		for (byte b : auth) { // variable length; null terminated
			ret.add(b);
		}

		// size is now index of sprite data
		int sprDataOffset = ret.size();
		byte[] sprDataOffsets = toByteArray(sprDataOffset);
		for (int i = 0; i < SPRITE_OFFSET_INDICES.length; i++) {
			ret.set(SPRITE_OFFSET_INDICES[i], sprDataOffsets[i]);
		}

		// add sprite data {
		for (byte b : spriteData) { // Size defined in SPRITE_DATA_SIZE
			ret.add(b);
		}

		// size is now index of pal data
		int palDataOffset = ret.size();
		byte[] palDataOffsets = toByteArray(palDataOffset);
		for (int i = 0; i < PAL_OFFSET_INDICES.length; i++) {
			ret.set(PAL_OFFSET_INDICES[i], palDataOffsets[i]);
		}

		// add palette data
		for (byte b : palData) { // Size defined in PAL_DATA_SIZE
			ret.add(b);
		}

		// add gloves data
		for (byte b : glovesData) { // 4 bytes
			ret.add(b);
		}

		// convert to a byte array
		int s = ret.size();
		dataStream = new byte[s];
		for (int i = 0; i < s; i++) {
			dataStream[i] = (Byte) ret.get(i);
		}

		// calculate checksum
		byte[] cksm = writeChecksum(dataStream);

		// add checksum to file
		for (int i = 0; i < CKSM_OFFSET_INDICES.length; i++) {
			dataStream[CKSM_OFFSET_INDICES[i]] = cksm[i];
		}
	}
	
	/**
	 * 
	 */
	private static byte[] writeChecksum(byte[] spr) {
		return null;
	}

	public boolean runSelfChecksum() throws BadChecksumException {
		refreshDataStream();
		return runChecksum(dataStream);
	}

	/**
	 * Should always return true if it works.
	 * @param spr
	 * @return
	 * @throws BadChecksumException
	 */
	public static boolean runChecksum(byte[] spr) throws BadChecksumException {
		// check sum whatever
		boolean badSum = 1 == 0;
		if (badSum) {
			throw new BadChecksumException();
		}
		return true;
	}

	/**
	 * Reads data from a filestream and creates a new {@code SPRFile} object.
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws ObsoleteSPRFormatException
	 * @throws BadChecksumException
	 */
	public static SPRFile readFile(String path) throws
		IOException, NotSPRException, ObsoleteSPRFormatException, BadChecksumException {
		if (!SpriteManipulator.testFileType(path, "spr")) {
			throw new NotSPRException();
		}

		byte[] zSPR = SpriteManipulator.readFile(path);

		// check SPR file header
		for (int i = 0; i < 4; i++) {
			if (zSPR[i] != FLAG[i]) {
				throw new ObsoleteSPRFormatException(
						"Obsolete file format; please convert to " + ZSPR_SPEC);
			}
		}

		// run a check sum
		runChecksum(zSPR);
		SPRFile ret = new SPRFile();

		// find the sprite name
		String spriteName = "";
		int loc = SPRITE_NAME_OFFSET;
		do {
			byte t = zSPR[loc++]; // we want to include the null terminator in the count
			if (t == 0) {
				break; // but not in the name
			}
			char temp = (char) t; 
			spriteName += temp;
		} while (zSPR[loc] != 0);

		ret.setSpriteName(spriteName);

		// find the author's name
		String authorName = "";
		// continue from this loc for author name
		do {
			byte t = zSPR[loc++]; // we want to include the null terminator in the count
			if (t == 0) {
				break; // but not in the name
			}
			char temp = (char) t; 
			authorName += temp;
		} while (zSPR[loc] != 0);

		ret.setAuthorName(authorName);

		// find sprite offset
		loc = 0;
		for (int i : SPRITE_OFFSET_INDICES) {
			loc |= zSPR[i];
			loc <<= 8;
		}

		// write sprite data
		byte[] sprData = new byte[SPRITE_DATA_SIZE];
		for (int i = 0; i < SPRITE_DATA_SIZE; i++, loc++) {
			sprData[i] = zSPR[loc];
		}

		ret.setSpriteData(sprData);

		// find palete offset
		loc = 0;
		for (int i : PAL_OFFSET_INDICES) {
			loc |= zSPR[i];
			loc <<= 8;
		}

		// write pal data
		byte[] palData = new byte[PAL_DATA_SIZE];
		for (int i = 0; i < PAL_DATA_SIZE; i++, loc++) {
			palData[i] = zSPR[loc];
		}
		
		ret.setPalData(palData);

		return ret;
	}
	
	/**
	 * Turns valid formats into an array of bytes.
	 * With regads to the name and author parameters,
	 * this function does not add the null terminator {@code \0}.
	 * <br /><br />
	 * @param o
	 * @return {@code byte[]} array according to follows:
	 * <table>
	 *   <caption>Accepted types</caption>
	 *   <tr>
	 *     <th >Type</th>
	 *     <th>Bytes</th>
	 *   </tr>
	 *   <tr>
	 *     <td>{@code Integer} <i>or</i> {@code int}</td>
	 *     <td>4</td>
	 *   </tr>
	 *   <tr>
	 *     <td>{@code Short} <i>or</i> {@code short}</td>
	 *     <td>2</td>
	 *   </tr>
	 *   <tr>
	 *     <td style="padding-right: 6px;">{@code Character[]} <i>or</i> {@code char}</td>
	 *     <td>Lenght of {@code o}</td>
	 *   </tr>
	 *   <tr>
	 *     <td>{@code String}</td>
	 *     <td>Lenght of {@code o}</td>
	 *   </tr>
	 * </table>
	 * All other types will return an empty array for safety reasons.
	 */
	private static byte[] toByteArray(Object o) {
		byte[] ret;
		if (o instanceof Integer) { // integer to 4 byte
			ret = new byte[] {
						(byte) ((SPRITE_SIZE_SHORT >> 24) & 0xFF),
						(byte) ((SPRITE_SIZE_SHORT >> 16) & 0xFF),
						(byte) ((SPRITE_SIZE_SHORT >> 8) & 0xFF),
						(byte) (SPRITE_SIZE_SHORT & 0xFF)
					};
		} else if (o instanceof Short) { // short to 2 byte
			ret = new byte[] {
						(byte) ((SPRITE_SIZE_SHORT >> 8) & 0xFF),
						(byte) (SPRITE_SIZE_SHORT & 0xFF)
					};
		} else if (o instanceof char[] || o instanceof Character[]) { // cast chars to bytes
			ret = charArrayToByteArray((char[]) o);
		} else if (o instanceof String) { // cast chars to bytes
			char[] temp = ((String) o).toCharArray();
			ret = charArrayToByteArray(temp);
		} else { // default to empty array
			ret = new byte[] {};
		}

		return ret;
	}

	/**
	 * Turns a character array into an array of bytes.
	 * This function should only be called by {@code toByteArray()},
	 * which should act as a wrapper for all byte conversions
	 * to prevent compatability issues if anything changes.
	 * @param ca
	 * @return
	 */
	private static byte[] charArrayToByteArray(char[] ca) {
		int l = ca.length;
		byte[] ret = new byte[l];
		for (int i = 0; i < l; i++) {
			ret[i] = (byte) ca[i];
		}
		return ret;
	}
}