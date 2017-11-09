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
	private static final byte[] FLAG = SpriteManipulator.FLAG;
	private static final byte[] ZSPR_VERSION = SpriteManipulator.ZSPR_VERSION;
	private static final String ZSPR_SPEC = SpriteManipulator.ZSPR_SPEC;
	private static final int[] CKSM_OFFSET_INDICES = SpriteManipulator.CKSM_OFFSET_INDICES;
	private static final int[] SPRITE_OFFSET_INDICES = SpriteManipulator.SPRITE_OFFSET_INDICES;
	private static final int[] PAL_OFFSET_INDICES = SpriteManipulator.PAL_OFFSET_INDICES;
	private static final int SPRITE_NAME_OFFSET = SpriteManipulator.SPRITE_NAME_OFFSET;

	/**
	 * <table>
	 *   <tr>
	 *     <th>Index</th>
	 *     <th>Block</th>
	 *     <th>Bytes</th>
	 *   </th>
	 *  <tr>
	 *    <td>0</td>
	 *    <td>flag</td>
	 *    <td>4</td>
	 *  </tr>
	 *  <tr>
	 *    <td>1</td>
	 *    <td>version</td>
	 *    <td>1</td>
	 *  </tr>
	 *  <tr>
	 *    <td>2</td>
	 *    <td>checksum</td>
	 *    <td>4</td>
	 *  </tr>
	 *  <tr>
	 *    <td>3</td>
	 *    <td>sprite data offset</td>
	 *    <td>4</td>
	 *  </tr>
	 *  <tr>
	 *    <td>4</td>
	 *    <td>sprite data size</td>
	 *    <td>2</td>
	 *  </tr>
	 *  <tr>
	 *    <td>5</td>
	 *    <td>pal data offset</td>
	 *    <td>4</td>
	 *  </tr>
	 *  <tr>
	 *    <td>6</td>
	 *    <td>pal data size</td>
	 *    <td>2</td>
	 *  </tr>
	 *  <tr>
	 *    <td>7</td>
	 *    <td>reserved</td>
	 *    <td>8</td>
	 *  </tr>
	 *  <tr>
	 *    <td>8</td>
	 *    <td>sprite name</td>
	 *    <td>...</td>
	 *  </tr>
	 * </table>
	 */
	private static final int[] BYTE_ALLOTMENTS =  SpriteManipulator.BYTE_ALLOTMENTS;
	@SuppressWarnings("unused")
	private static final int FLAG_SIZE = BYTE_ALLOTMENTS[0];
	@SuppressWarnings("unused")
	private static final int VERSION_SIZE = BYTE_ALLOTMENTS[1];
	private static final int CHECKSUM_SIZE = BYTE_ALLOTMENTS[2];
	private static final int SPRITE_OFFSET_SIZE = BYTE_ALLOTMENTS[3];
	@SuppressWarnings("unused")
	private static final int SPRITE_DATA_INFO_SIZE = BYTE_ALLOTMENTS[4];
	private static final int PAL_OFFSET_SIZE = BYTE_ALLOTMENTS[5];
	@SuppressWarnings("unused")
	private static final int PAL_DATA_INFO_SIZE = BYTE_ALLOTMENTS[6];
	private static final int RESERVED_SIZE = BYTE_ALLOTMENTS[7];
	private static final int NAME_ROM_MAX_LENGTH = SpriteManipulator.NAME_ROM_MAX_LENGTH;

	// class constants
	// data sizes for sprites
	private static final int SPRITE_DATA_SIZE = SpriteManipulator.SPRITE_DATA_SIZE;
	private static final short SPRITE_SIZE_SHORT = (short) SPRITE_DATA_SIZE; // cast to not get extra bytes
	private static final int PAL_DATA_SIZE = SpriteManipulator.PAL_DATA_SIZE;
	private static final short PAL_SIZE_SHORT = (short) PAL_DATA_SIZE; // cast to not get extra bytes
	private static final int GLOVE_DATA_SIZE = SpriteManipulator.GLOVE_DATA_SIZE;

	// local vars
	private byte[] spriteData;
	private byte[] palData;
	private byte[] glovesData;
	private String spriteName;
	private String authorName;
	private String authorNameROM;
	private byte[] dataStream;

	// default constructor
	public SPRFile() {
		// set gloves data to default just in case
		glovesData = new byte[GLOVE_DATA_SIZE];
	}

	// constructor
	public SPRFile(byte[] spriteData, byte[] palData, byte[] glovesData,
			String spriteName, String authorName) {
		this.spriteData = spriteData;
		this.palData = palData;
		this.glovesData = glovesData;
		this.spriteName = spriteName;
		this.authorName = authorName;
		this.authorNameROM = authorName;
		autoFixAuthorNameROM();
	}

	// no sprite name or author name
	public SPRFile(byte[] spriteData, byte[] palData, byte[] glovesData) {
		this(spriteData, palData, glovesData, "", "");
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
	
	public void setAuthorNameROM(String authorNameROM) {
		this.authorNameROM = authorNameROM;
		autoFixAuthorNameROM();
	}

	public String getAuthorNameROM() {
		return this.authorNameROM;
	}

	public String toString() {
		String spr = (this.spriteName == null) ? "Untitled" : this.spriteName;
		String nom = (this.authorName == null) ? "Unknown" : this.authorName;
		return String.format("'%s' by %s", spr, nom);
	}

	/**
	 * Fixes {@code authorNameROM} to only contain ASCII characters.
	 */
	public void autoFixAuthorNameROM() {
		String autoName = "";
		char[] authorSplit = authorNameROM.toCharArray();
		int nameLength = 0;
		for (int i = 0; i < authorSplit.length; i++) {
			char cur = authorSplit[i];
			short test = (short) cur;
			// skip characters outside of byte's range
			if (test > 255) {
				continue;
			}
			// add any characters within ascii range
			autoName += cur;
			nameLength++;
			if (nameLength == NAME_ROM_MAX_LENGTH) {
				break;
			}
		}

		// set fixed name
		authorNameROM = autoName;
	}

	public void setNameFromPath(String path) {
		// find file name from path
		int dl = path.lastIndexOf('.');
		int sl = path.lastIndexOf('\\');
		String sprName;

		if (sl == -1) { // if not full path, use just up until extension
			sprName = path.substring(0, dl);
		} else { // if longer path, find what's between '\' and '.'
			sprName = path.substring(sl + 1, dl);
		}

		this.spriteName = sprName;
	}

	/**
	 * Gets data stream after forcing any changes
	 */
	public byte[] getDataStream() {
		refreshDataStream();
		return dataStream;
	}

	/**
	 * Creates a data stream for this sprite
	 */
	public void refreshDataStream() {
		ArrayList<Byte> ret = new ArrayList<Byte>();

		// add header
		for (byte b : FLAG) {
			ret.add(b);
		}

		// add version
		for (byte b : ZSPR_VERSION) {
			ret.add(b);
		}

		// add checksum - default to 0000FFFF to start
		byte[] defaultCksm = new byte[] {
				0x00, 0x00,
				(byte) 0xFF, (byte) 0xFF // ffs signed bytes
			};
		for (byte b : defaultCksm) {
			ret.add(b);
		}

		// add sprite data offset, start with 0s
		for (byte b : new byte[SPRITE_OFFSET_SIZE]) {
			ret.add(b);
		}

		// add sprite size (constant)
		for (byte b : toByteArray(SPRITE_SIZE_SHORT)) {
			ret.add(b);
		}

		// add palette data offset, start with 0s
		for (byte b : new byte[PAL_OFFSET_SIZE]) {
			ret.add(b);
		}

		// add palette size (constant)
		for (byte b : toByteArray(PAL_SIZE_SHORT)) {
			ret.add(b);
		}

		// add reserved (constant size)
		for (byte b : new byte[RESERVED_SIZE]) {
			ret.add(b);
		}

		// convert to byte arrays
		byte[] sName = toByteArray(spriteName + '\0'); // add null terminators here
		byte[] auth = toByteArray(authorName + '\0');

		// add sprite name
		for (byte b : sName) { // variable length; null terminated
			ret.add(b);
		}

		// add author name
		for (byte b : auth) { // variable length; null terminated
			ret.add(b);
		}

		// treat authorNameROM differently as it's ASCII, not UTF-16LE
		char[] authROM = (authorNameROM + '\0').toCharArray(); // add null terminator here
		for (char c : authROM) {
			ret.add((byte) c);
		}

		// size is now index of sprite data
		int sprDataOffset = ret.size();

		byte[] sprDataOffsets = toByteArray(sprDataOffset);
		for (int i = 0; i < SPRITE_OFFSET_INDICES.length; i++) {
			ret.set(SPRITE_OFFSET_INDICES[i], sprDataOffsets[i]);
		}

		// add sprite data
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
		for (byte b : glovesData) {
			ret.add(b);
		}

		// convert to a byte array
		int s = ret.size();
		dataStream = new byte[s];
		for (int i = 0; i < s; i++) {
			dataStream[i] = (Byte) ret.get(i);
		}

		// calculate checksum
		byte[] chalksome = calcChecksum(dataStream);

		// add checksum to file
		for (int i = 0; i < CHECKSUM_SIZE; i++) {
			dataStream[CKSM_OFFSET_INDICES[i]] = chalksome[i];
		}
	}

	/**
	 * 
	 */
	private static byte[] calcChecksum(byte[] spr) {
		byte[] ret = new byte[CHECKSUM_SIZE];
		int cksm = 0;

		for (byte b : spr) {
			int b2 = Byte.toUnsignedInt(b);
			cksm += b2;
		}

		cksm &= 0xFFFF;
		ret[0] = (byte) (cksm & 0xFF);
		ret[1] = (byte) ((cksm >> 8) & 0xFF);

		int comp = cksm ^ 0xFFFF;
		ret[2] = (byte) (comp & 0xFF);
		ret[3] = (byte) ((comp >> 8) & 0xFF);

		return ret;
	}

	/**
	 * Checks its own priviledge
	 * @return
	 * @throws BadChecksumException
	 */
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
		byte[] myCksm = new byte[CHECKSUM_SIZE]; // stored checksum
		for (int i = 0; i < CHECKSUM_SIZE; i++) {
			myCksm[i] = spr[CKSM_OFFSET_INDICES[i]];
		}

		byte[] chestsum = calcChecksum(spr); // test checksum
		boolean badSum = false;
		for (int i = 0; i < CHECKSUM_SIZE; i++) {
			if (myCksm[i] != chestsum[i]) {
				badSum = true;
			}
		}

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
		boolean nullFound = false;
		do {
			byte t1 = zSPR[loc++]; // we want to include the null terminator in the count
			byte t2 = zSPR[loc++];
			short s = 0;
			s |= t2; // little endian, so t2 first for java char casting
			s <<= 8;
			s |= t1;
			if (s == 0) { // if both bytes are 0, it's null byte
				nullFound = true;
				continue;
			}
			char temp = (char) s; 
			spriteName += temp;
		} while (!nullFound);

		ret.setSpriteName(spriteName);

		// find the author's name
		String authorName = "";

		// continue from this loc for author name
		nullFound = false;
		do {
			byte t1 = zSPR[loc++]; // we want to include the null terminator in the count
			byte t2 = zSPR[loc++];
			short s = 0;
			s |= t2; // little endian, so t2 first for java char casting
			s <<= 8;
			s |= t1;
			if (s == 0) { // if both bytes are 0, it's null byte
				nullFound = true;
				continue;
			}
			char temp = (char) s; 
			authorName += temp;
		} while (!nullFound);

		ret.setAuthorName(authorName);

		// find the author's name ASCII
		String authorNameROM = "";

		// continue from this loc for author name
		nullFound = false;
		do {
			byte b = zSPR[loc++]; // we want to include the null terminator in the count
			if (b == 0) { // if byte is 0, it's null byte
				nullFound = true;
				continue;
			}
			char temp = (char) b; 
			authorNameROM += temp;
		} while (!nullFound);

		ret.setAuthorNameROM(authorNameROM);

		// find sprite offset
		loc = 0;
		for (int i : SPRITE_OFFSET_INDICES) {
			loc <<= 8;
			loc |= zSPR[i];
		}
		loc = Integer.reverseBytes(loc); // reverse because LE

		// write sprite data
		byte[] sprData = new byte[SPRITE_DATA_SIZE];
		for (int i = 0; i < SPRITE_DATA_SIZE; i++, loc++) {
			sprData[i] = zSPR[loc];
		}

		ret.setSpriteData(sprData);

		// find palete offset
		loc = 0;
		for (int i : PAL_OFFSET_INDICES) {
			loc <<= 8;
			loc |= zSPR[i];
		}
		loc = Integer.reverseBytes(loc); // reverse because LE

		// write pal data
		byte[] palData = new byte[PAL_DATA_SIZE];
		for (int i = 0; i < PAL_DATA_SIZE; i++, loc++) {
			palData[i] = zSPR[loc];
		}

		ret.setPalData(palData);

		// find and write gloves data
		byte[] glovesData = new byte[GLOVE_DATA_SIZE];
		for (int i = 0; i < GLOVE_DATA_SIZE; i++, loc++) { // continue from end of palette
			glovesData[i] = zSPR[loc];
		}

		ret.setGlovesData(glovesData);

		// return new sprfile object
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
	 *     <th>Type</th>
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
	 *     <td style="padding-right: 6px;">{@code Character[]} <i>or</i> {@code char[]}</td>
	 *     <td>Length of {@code o} * 2</td>
	 *   </tr>
	 *   <tr>
	 *     <td>{@code String}</td>
	 *     <td>Length of {@code o} * 2</td>
	 *   </tr>
	 * </table>
	 * All other types will return an empty array for safety reasons.
	 */
	private static byte[] toByteArray(Object o) {
		byte[] ret;
		if (o instanceof Integer) { // integer to 4 byte
			int temp = (int) o;
			ret = new byte[] {
						(byte) (temp & 0xFF),
						(byte) ((temp >> 8) & 0xFF),
						(byte) ((temp >> 16) & 0xFF),
						(byte) ((temp >> 24) & 0xFF)
					};
		} else if (o instanceof Short) { // short to 2 byte
			short temp = (short) o;
			ret = new byte[] {
						(byte) (temp & 0xFF),
						(byte) ((temp >> 8) & 0xFF),
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
		byte[] ret = new byte[l * 2];
		for (int i = 0; i < l; i++) {
			short c = (short) ca[i];
			byte bp = (byte) ((c >> 8) & 0xFF);
			byte lp = (byte) (c & 0xFF);
			ret[i*2] = lp; // little endian
			ret[i*2+1] = bp;
		}
		return ret;
	}
}