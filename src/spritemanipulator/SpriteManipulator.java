package spritemanipulator;

import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class SpriteManipulator {
	// ALTTPNG
	public static final String ALTTP_VERSION = "v1.7.0";

	// ZSPR file format specifications
	// Time stamp: 7 Nov 2017
	public static final int[] BYTE_ALLOTMENTS = new int[] { // for calculating offsets
		4, // 0: header
		1, // 1: version
		4, // 2: checksum
		4, // 3: sprite data offset
		2, // 4: sprite data size
		4, // 5: pal data offset
		2, // 6: pal data size
		2, // 7: sprite type (0x01 00 = player)
		6, // 8: reserved
			// 9 : sprite name
	};
	public static final byte[] FLAG = { 'Z', 'S', 'P', 'R' };
	public static final byte[] ZSPR_VERSION = { 1 }; // only 1 byte, but array for future proofing
	public static final String ZSPR_VERSION_TAG = "v1.0";
	public static final String ZSPR_SPEC =
			String.format("ZSPR (.ZSPR) version %s specification", ZSPR_VERSION_TAG);
	public static final int[] CHECKSUM_INDICES = getIndices(2); // where to find the checksum in file
	public static final int[] SPRITE_OFFSET_INDICES = getIndices(3); // where to find the sprite offset in file
	public static final int[] PAL_OFFSET_INDICES = getIndices(5); // where to find the palette offset in file
	public static final int[] TYPE_INDICES = getIndices(7); // where to find the checksum in file
	public static final int SPRITE_NAME_OFFSET = calcOffset(8);
	public static final int NAME_ROM_MAX_LENGTH = 20;

	/**
	 * Calculates the BEGINNING index based on the allotted bytes of all previous items;
	 * e.g. {@code calcOffset(3)} will find the offset for the block {@code Sprite data offset}
	 * by adding together the number of bytes allotted to the previous 2 blocks.
	 * @param i
	 * @return
	 */
	private static int calcOffset(int i) {
		int ret = 0;
		for (int j = 0; j < i; j++) {
			ret += BYTE_ALLOTMENTS[j];
		}
		return ret;
	}

	/**
	 * Calculates the beginning index and then the remaining alloted bytes.
	 */
	private static int[] getIndices(int i) {
		int[] ret = new int[BYTE_ALLOTMENTS[i]];
		int p = calcOffset(i);
		for (int j = 0; j < BYTE_ALLOTMENTS[i]; j++, p++) {
			ret[j] = p;
		}
		return ret;
	}

	// class constants
	// data sizes for sprites
	public static final int SPRITE_BLOCK_COUNT = 896;
	public static final int SPRITE_BLOCK_SIZE = 32;
	public static final int SPRITE_DATA_SIZE = SPRITE_BLOCK_COUNT * SPRITE_BLOCK_SIZE; // 28672

	// data for operating with palettes
	public static final int MAIL_PALETTE_SIZE = 16;
	public static final int ALL_MAILS_PALETTE_SIZE = MAIL_PALETTE_SIZE * 4;
	public static final int ALL_MAILS_WITH_GLOVES_SIZE = ALL_MAILS_PALETTE_SIZE + 2;
	public static final int PAL_DATA_SIZE = 120; // 4 palettes, 15 colors, 2 bytes per color
	public static final int GLOVE_DATA_SIZE = 4; // 2 colors, 2 bytes each
	public static final byte[] VANILLA_GLOVE_COLORS =
			new byte[] { (byte) 0xF6, (byte) 0x52, (byte) 0x76, (byte) 0x03 };

	// ROM offsets
	public static final int SPRITE_OFFSET = 0x80000;
	public static final int PAL_OFFSET = 0x0DD308;
	public static final int[] GLOVE_OFFSETS =
			new int[] { 0xDEDF5, 0xDEDF6, 0xDEDF7, 0xDEDF8 }; // gloves, gloves, mitts, mitts

	// data sizes for images
	public static final int SPRITE_SHEET_WIDTH = 128;
	public static final int SPRITE_SHEET_HEIGHT = 448;
	public static final int INDEXED_RASTER_SIZE = SPRITE_SHEET_WIDTH * SPRITE_SHEET_HEIGHT;
	public static final int ABGR_RASTER_SIZE = INDEXED_RASTER_SIZE * 4;

	// format of snes 4bpp {row (r), bit plane (b)}
	// bit plane 0 indexed such that 1011 corresponds to 0123
	public static final int BPPI[][] = new int[][] {
			{0,0},{0,1},{1,0},{1,1},{2,0},{2,1},{3,0},{3,1},
			{4,0},{4,1},{5,0},{5,1},{6,0},{6,1},{7,0},{7,1},
			{0,2},{0,3},{1,2},{1,3},{2,2},{2,3},{3,2},{3,3},
			{4,2},{4,3},{5,2},{5,3},{6,2},{6,3},{7,2},{7,3}
	};

	// A (currently) unchanging palette switched to by the game when link is electrocuted
	private static final byte[][] ZAP_PALETTE = new byte[][] {
			{ 0, 0, 0},
			{ 0, 0, 0},
			{ -48, -72, 24},
			{ -120, 112, -8},
			{ 0, 0, 0},
			{ -48, -64, -8},
			{ 0, 0, 0},
			{ -48, -64, -8},
			{ 112, 88, -32},
			{ -120, 112, -8},
			{ 56, 40, -128},
			{ -120, 112, -8},
			{ 56, 40, -128},
			{ 72, 56, -112},
			{ 120, 48, -96},
			{ -8, -8, -8}
	};

	/**
	 * Prevent instantiation
	 */
	private SpriteManipulator() {}

	/**
	 * Indexes an image based on a palette.
	 * Assumes ABGR color space.
	 * <br><br>
	 * If a color matches an index that belongs to one of the latter 3 mails
	 * but does not match anything in green mail,
	 * then it is treated as the color at the corresponding index of green mail.
	 * 
	 * @param pixels - aray of color indices
	 * @param pal - palette colors
	 */
	public static byte[] index(byte[] pixels, int[] pal) {
		// all 8x8 squares, read left to right, top to bottom
		byte[] ret = new byte[INDEXED_RASTER_SIZE];

		// read image
		for (int i = 0; i < INDEXED_RASTER_SIZE; i++) {
			int pos = i * 4;
			// get each color and get rid of sign
			// colors are stored as {A,B,G,R,A,B,G,R...}
			int b = Byte.toUnsignedInt(pixels[pos+1]);
			int g = Byte.toUnsignedInt(pixels[pos+2]);
			int r = Byte.toUnsignedInt(pixels[pos+3]);

			int rgb = toRGB9(r, g, b); // convert to 9 digits

			// find palette index of current pixel
			for (int s = 0; s < pal.length; s++) {
				if (pal[s] == rgb) {
					ret[i] = (byte) (s % MAIL_PALETTE_SIZE); // mod 16 in case it reads another mail
					break;
				}
			}
		}
		return ret;
	}

	/**
	 * Turn the image into an array of 8x8 blocks.
	 * 
	 * @param pixels - aray of color indices
	 * @return {@code byte[][][]} representing the image as a grid of color indices
	 */
	public static byte[][][] get8x8(byte[] pixels) {
		int largeCol = 0;
		int intRow = 0;
		int intCol = 0;
		int index = 0;

		// all 8x8 squares, read left to right, top to bottom
		byte[][][] eightbyeight = new byte[SPRITE_BLOCK_COUNT][8][8];

		// read image raster
		for (int i = 0; i < INDEXED_RASTER_SIZE; i++) {
			eightbyeight[index][intRow][intCol] = pixels[i];

			// count up square by square
			// at 8, reset the "Interior column" which we use to locate the pixel in 8x8
			// increments the "Large column", which is the index of the 8x8 sprite on the sheet
			// at 16, reset the index and move to the next row
			// (so we can wrap around back to our old 8x8)
			// after 8 rows, undo the index reset, and move on to the next super row
			intCol++;
			if (intCol == 8) {
				index++;
				largeCol++;
				intCol = 0;
				if (largeCol == 16) {
					index -= 16;
					largeCol = 0;
					intRow++;
					if (intRow == 8) {
						index += 16;
						intRow = 0;
					} // end intRow if
				} // end largeCol if
			} // end intCol if
		} // end loop

		return eightbyeight;
	}

	/**
	 * Indexes a sprite and turns into 8x8 in one go.
	 * <br>
	 * See: {@link #index(byte[], int[])}, {@link #get8x8(byte[])}
	 */
	public static byte[][][] indexAnd8x8(byte[] pixels, int[] palette) {
		return get8x8(index(pixels, palette));
	}

	/**
	 * Takes a sprite and turns it into 896 blocks of 8x8 pixels.
	 * @param sprite
	 */
	public static byte[][][] makeSpr8x8(byte[] sprite) {
		byte[][][] ret = new byte[SPRITE_BLOCK_COUNT][8][8];

		// current block we're working on, each sized 32
		int b = -1; // start at -1 since we're incrementing at 0mod32

		int g; // locates where in interlacing map we're reading from

		for (int i = 0; i < SPRITE_DATA_SIZE; i++) {
			g = i % SPRITE_BLOCK_SIZE; // find interlacing index
			if (g == 0) { b++; } // increment at 0th index

			int r = BPPI[g][0]; // row to look at
			int p = BPPI[g][1]; // bit plane of byte
			byte q = sprite[i]; // byte to unravel

			for (int c = 0; c < 8; c++) { // run through the byte
				boolean bitOn = (q & (1 << (7-c))) != 0; // AND with 1 shifted to the correct plane
				if (bitOn) { // if on (true), OR with that plane in index map
					ret[b][r][c] |= (1 << p);
				}
			}
		}

		return ret;
	}

	/**
	 * Splits a palette into RGB arrays.
	 * Only uses the first 16 colors.
	 * Automatically makes first index black.
	 */
	public static byte[][] getPal(byte[] pal) {
		byte[][] ret = new byte[ALL_MAILS_PALETTE_SIZE][3];
		int byteLoc = 1;
		for (int i = 0; i < ALL_MAILS_PALETTE_SIZE; i++) {
			if (i % MAIL_PALETTE_SIZE == 0) {
				ret[0][0] = 0;
				ret[0][1] = 0;
				ret[0][2] = 0;
			} else {
				short color = 0;
				int pos = (byteLoc++ * 2) - 2;
				color = (short) Byte.toUnsignedInt(pal[pos+1]);
				color <<= 8;
				color |= (short) Byte.toUnsignedInt(pal[pos]);

				ret[i][0] = (byte) (((color >> 0) & 0x1F) << 3);
				ret[i][1] = (byte) (((color >> 5) & 0x1F) << 3);
				ret[i][2] = (byte) (((color >> 10) & 0x1F) << 3);
			}
		}

		return ret;
	}

	/**
	 * Get 16 mail colors from a large palette
	 * @param pal
	 * @return
	 */
	public static byte[][] getSubpal(byte[][] pal, int palIndex) {
		byte[][] ret = new byte[MAIL_PALETTE_SIZE][3];
		int pos = palIndex * MAIL_PALETTE_SIZE;

		for (int i = 0; i < MAIL_PALETTE_SIZE; i++, pos++) {
				ret[i] = pal[pos];
		}

		return ret;
	}

	/**
	 * Turn index map in 8x8 format into an array of ABGR values.
	 */
	public static byte[] makeRaster(byte[][][] ebe, byte[][] palette) {
		byte[] ret = new byte[ABGR_RASTER_SIZE];
		byte[] color;
		int largeCol = 0;
		int intRow = 0;
		int intCol = 0;
		int index = 0;

		// read image raster
		for (int i = 0; i < ABGR_RASTER_SIZE / 4; i++) {
			int pos = i * 4;
			byte coli = ebe[index][intRow][intCol]; // get pixel color index
			color = palette[coli]; // get palette color
			
			if (coli == 0) { // index 0 = trans
				ret[pos] = 0;
			} else {
				ret[pos] = (byte) 255;
			}

			// BGR
			ret[pos+1] = color[2];
			ret[pos+2] = color[1];
			ret[pos+3] = color[0];

			// count up square by square
			// at 8, reset the "Interior column" which we use to locate the pixel in 8x8
			// increments the "Large column", which is the index of the 8x8 sprite on the sheet
			// at 16, reset the index and move to the next row
			// (so we can wrap around back to our old 8x8)
			// after 8 rows, undo the index reset, and move on to the next super row
			intCol++;
			if (intCol == 8) {
				index++;
				largeCol++;
				intCol = 0;
				if (largeCol == 16) {
					index -= 16;
					largeCol = 0;
					intRow++;
					if (intRow == 8) {
						index += 16;
						intRow = 0;
					} // end intRow if
				} // end largeCol if
			} // end intCol if
		} // end loop

		return ret;
	}

	/**
	 * Turns a 4 byte raster {A,B,G,R} into an integer array and sets the image.
	 * @param raster
	 * @return
	 */
	public static BufferedImage makeSheet(byte[] raster) {
		BufferedImage image =
			new BufferedImage(SPRITE_SHEET_WIDTH, SPRITE_SHEET_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		int[] rgb = new int[INDEXED_RASTER_SIZE];
		for (int i = 0, j = 0; i < rgb.length; i++) {
			int a = raster[j++] & 0xFF;
			int b = raster[j++] & 0xFF;
			int g = raster[j++] & 0xFF;
			int r = raster[j++] & 0xFF;
			rgb[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}

		image.setRGB(0, 0, SPRITE_SHEET_WIDTH, SPRITE_SHEET_HEIGHT, rgb, 0, SPRITE_SHEET_WIDTH);

		return image;
	}

	/**
	 * Makes 5 separate images: green mail, blue mail, red mail, bunny, zapped.
	 * @param eightbyeight
	 * @param pal
	 */
	public static BufferedImage[] makeAllMails(byte[][][] eightbyeight, byte[][] pal) {
		BufferedImage[] ret = new BufferedImage[5];
		byte[][] subpal;
		byte[] raster;

		for (int i = 0; i < 4; i++) {
			subpal = getSubpal(pal, i);
			raster = makeRaster(eightbyeight, subpal);
			ret[i] = makeSheet(raster);
		}

		raster = makeRaster(eightbyeight, ZAP_PALETTE);
		ret[4] = makeSheet(raster);

		return ret;
	}

	/**
	 * Patches an {@link SPRFile} into a ROM.
	 * @param romTarget
	 * @param spr
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void patchRom(String romTarget, ZSPRFile spr) throws IOException {
		// get ROM data
		byte[] romStream;
		FileInputStream fsInput = new FileInputStream(romTarget);
		romStream = new byte[(int) fsInput.getChannel().size()];
		fsInput.read(romStream);
		fsInput.getChannel().position(0);
		fsInput.close();

		FileOutputStream fsOut = new FileOutputStream(romTarget);

		// grab relevant data from zspr file
		byte[] sprData = spr.getSpriteData();
		byte[] palData = spr.getPalData();
		byte[] glovesData = spr.getGlovesData();

		for(int i = 0; i < SPRITE_DATA_SIZE; i++) {
			romStream[SPRITE_OFFSET + i] = sprData[i];
		}

		// Check to see if glove colors are defined
		boolean noneSet = true;
		for (byte b : glovesData) {
			if (b != 0) {
				noneSet = false;
				break;
			}
		}

		// if not defined, skip this step
		// otherwise write to the correct indices
		if (noneSet) {
			// do nothing
		} else {
			for (int i = 0; i < 4; i++) {
				romStream[GLOVE_OFFSETS[i]] = glovesData[i];
			}
		}

		// add palette data to ROM
		for (int i = 0; i < PAL_DATA_SIZE; i++) {
			romStream[PAL_OFFSET + i] = palData[i];
		}

		fsOut.write(romStream, 0, romStream.length);
		fsOut.close();
	}

	/**
	 * Reads a ROM to create a sprite data stream.
	 * @param romPath
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] getSprFromROM(String romPath) throws IOException {
		byte[] ROM = readFile(romPath);
		byte[] ret = new byte[SPRITE_DATA_SIZE];

		for (int i = 0; i < SPRITE_DATA_SIZE; i++) {
			ret[i] = ROM[SPRITE_OFFSET + i];
		}

		return ret;
	}

	/**
	 * Reads a ROM to create a pal data stream.
	 * @param romPath
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] getPalFromROM(String romPath) throws IOException {
		byte[] ROM = readFile(romPath);
		byte[] ret = new byte[PAL_DATA_SIZE];

		for (int i = 0; i < PAL_DATA_SIZE; i++) {
			ret[i] = ROM[PAL_OFFSET + i];
		}

		return ret;
	}

	/**
	 * Reads a ROM to get gloves data
	 * @param romPath
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] getGlovesFromROM(String romPath) throws IOException {
		byte[] ROM = readFile(romPath);
		byte[] ret = new byte[GLOVE_DATA_SIZE];

		for (int i = 0; i < GLOVE_DATA_SIZE; i++) {
			ret[i] = ROM[GLOVE_OFFSETS[i]];
		}

		return ret;
	}

	/**
	 * Converts an index map into a proper 4BPP (SNES) byte map.
	 * @param eightbyeight - color index map
	 * @return new byte array in SNES4BPP format
	 */
	public static byte[] export8x8ToSPR(byte[][][] eightbyeight) {
		byte[] fourbpp = new byte[SPRITE_DATA_SIZE];
		int pos = 0;

		for (int i = 0; i < SPRITE_BLOCK_COUNT; i++) { // for each block
			for (int j = 0; j < SPRITE_BLOCK_SIZE; j++) { // each byte, as per bppi
				byte b = 0;
				for (int k = 0; k < 8; k++) {
					// get row's kth bit plane, based on index j of bppi
					int row = BPPI[j][0];
					int plane = BPPI[j][1];
					int byteX = eightbyeight[i][row][k];

					// AND the bits with 1000, 0100, 0010, 0001 to get bit in that location
					boolean bitOn = (byteX & (1 << plane)) > 0;
					b <<= 1;
					if (bitOn) { b |= 1; }
				} // end 8 bits of byte calculation
				fourbpp[pos++] = b;
			} // end 32 bytes for 8x8 block
		} // end 896 blocks of 8x8
		return fourbpp;
	}

	/**
	 * Create binary palette data for appending to the end of the {@code .zspr} file.
	 * @param pal - 64/66 length {@code int[]} containing the palette colors as RRRGGGBBB
	 * @return {@code byte[]} containing palette data in 5:5:5 format
	 */
	public static byte[] getPalDataFromArray(int[] pal) {
		ByteBuffer palRet = ByteBuffer.allocate(PAL_DATA_SIZE); // create palette data as 5:5:5

		for (int i = 1; i < MAIL_PALETTE_SIZE; i++) {
			for (int t = 0; t < 4; t++) {
				int cur = pal[i + (MAIL_PALETTE_SIZE * t)];
				int r = cur / 1000000;
				int g = (cur % 1000000) / 1000;
				int b = cur % 1000;
				short s = (short) ((( b / 8) << 10) | (( g / 8) << 5) | (( r / 8) << 0));
				// put color into every mail palette
				palRet.putShort(30*t+((i-1)*2),Short.reverseBytes(s));
			}
		}

		return palRet.array();
	}

	/**
	 * Finds binary gloves data from the last 2 indices of the palette.
	 * If the palette has no gloves data (it is only 64 colors), return null data.
	 */
	public static byte[] getGlovesDataFromArray(int[] pal) {
		int l = pal.length;
		assert l == ALL_MAILS_PALETTE_SIZE || l == ALL_MAILS_WITH_GLOVES_SIZE;

		if (l != ALL_MAILS_WITH_GLOVES_SIZE) { // when no data defined in here
			return new byte[] { 0, 0, 0, 0 };
		}

		ByteBuffer palRet = ByteBuffer.allocate(GLOVE_DATA_SIZE);
		int loc = ALL_MAILS_PALETTE_SIZE; // start at end of palette

		for (int i = 0; i < 2; i++, loc++) {
			int cur = pal[loc];
			int r = cur / 1000000;
			int g = (cur % 1000000) / 1000;
			int b = cur % 1000;

			short s = (short) ((( b / 8) << 10) | (( g / 8) << 5) | (( r / 8) << 0));
			palRet.putShort(i*2,Short.reverseBytes(s)); // put color
		}

		return palRet.array();
	}

	/**
	 * Takes every color in a palette and rounds each byte to the nearest 8.
	 * @param pal - palette to round
	 */
	public static int[] roundPalette(int[] pal) {
		int[] ret = new int[pal.length];
		for (int i = 0; i < pal.length; i++) {
			int cur = pal[i];
			int r = cur / 1000000;
			int g = (cur % 1000000) / 1000;
			int b = cur % 1000;
			r = roundVal(r);
			g = roundVal(g);
			b = roundVal(b);
			ret[i] = toRGB9(r,g,b);
		}
		return ret;
	}

	/**
	 * Rounds every byte in an image to the nearest 8.
	 * @param raster - image raster to round
	 */
	public static byte[] roundRaster(byte[] raster) {
		byte[] ret = new byte[raster.length];
		for (int i = 0; i < raster.length; i++) {
			int v = Byte.toUnsignedInt(raster[i]);
			v = roundVal(v);
			ret[i] = (byte) v;
		}
		return ret;
	}

	public static int roundVal(int v) {
		int ret = (v / 8) * 8;
		return ret;
	}

	/**
	 * Rounds each color value to the nearest 8, and concatenates them into a single number
	 * whose digits are RRRGGGBBB
	 * @param r
	 * @param g
	 * @param b
	 */
	public static int toRGB9(int r, int g, int b) {
		r = roundVal(r);
		g = roundVal(g);
		b = roundVal(b);
		return (r * 1000000) + (g * 1000) + b;
	}

	/**
	 * Converts to ABGR colorspace
	 * @param img - image to convert
	 * @return New {@code BufferredImage} in the correct colorspace
	 */

	public static BufferedImage convertToABGR(BufferedImage img) {
		BufferedImage ret = null;
		ret = new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_4BYTE_ABGR);
		ColorConvertOp rgb = new ColorConvertOp(null);
		rgb.filter(img,ret);
		return ret;
	}

	/**
	 * Get the full image raster
	 * @param img - image to read
	 */
	public static byte[] getImageRaster(BufferedImage img) {
		byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		return pixels;
	}

	/**
	 * Reads a file
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static byte[] readFile(String path) throws IOException {
		File file = new File(path);
		byte[] ret = new byte[(int) file.length()];
		FileInputStream s;
		try {
			s = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw e;
		}
		try {
			s.read(ret);
			s.close();
		} catch (IOException e) {
			throw e;
		}

		return ret;
	}

	/**
	 * Writes the image to a {@code .zspr} file.
	 * @param map - SNES 4BPP file, including 5:5:5
	 * @param loc - File path of exported sprite
	 */
	public static void writeFile(byte[] map, String loc) throws IOException {
		new File(loc); // create a file at directory

		FileOutputStream fileOuputStream = new FileOutputStream(loc);
		try {
			fileOuputStream.write(map);
		} finally {
			fileOuputStream.close();
		}
	}

	/**
	 * Test a file against a single extension.
	 * 
	 * @param s - file name or extension
	 * @param type - extension
	 * @return {@code true} if extension is matched
	 */
	public static boolean testFileType(String s, String type) {
		String filesType = getFileType(s);
		return filesType.equalsIgnoreCase(type);
	}

	/**
	 * Test a file against multiple extensions
	 * 
	 * @param s - file name or extension
	 * @param type - extensions
	 * @return {@code true} if extension is matched
	 */
	public static boolean testFileType(String s, String[] type) {
		String filesType = getFileType(s);
		boolean match = false;
		for (String t : type) {
			if (filesType.equalsIgnoreCase(t)) {
				match = true;
				break;
			}
		}
		return match;
	}

	/**
	 * gives file extension name from a string
	 * @param s - test case
	 * @return extension type
	 */
	public static String getFileType(String s) {
		String ret = s.substring(s.lastIndexOf(".") + 1);
		return ret;
	}

	/**
	 * Turns an array from {@code int} to {@code byte}.
	 */
	public static byte[][] convertArray(int[][] c) {
		byte[][] ret = new byte[c.length][c[0].length];
		for (int i = 0; i < ret.length; i++) {
			for (int j = 0; j < ret[i].length; j++) {
				ret[i][j] = (byte) c[i][j];
			}
		}
		return ret;
	}

	/**
	 * Writes a ZSPR file to the file name provided
	 * @param loc
	 * @param s
	 * @throws IOException
	 * @throws NotZSPRException
	 * @throws BadChecksumException
	 */
	public static void writeSPRFile(String path, ZSPRFile s)
			throws IOException, ZSPRFormatException {
		int dl = path.lastIndexOf('.');

		// test file type
		if (dl == -1) { // no extension
			throw new IOException();
		} else if (!testFileType(path, ZSPRFile.EXTENSION)) { // file is not .zspr
			throw new ZSPRFormatException("File is not a " + ZSPRFile.EXTENSION + " file.");
		}

		// test sprite name
		if (s.getSpriteName().equals("")) {
			s.setNameFromPath(path);
		}

		s.runSelfChecksum();
		byte[] file = s.getDataStream();
		writeFile(file, path);
	}
}