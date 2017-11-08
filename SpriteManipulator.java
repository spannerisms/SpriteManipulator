package SpriteManipulator;

import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public abstract class SpriteManipulator {
	// SPR file format specifications
	// Time stamp: 7 Nov 2017
	public static final byte[] FLAG = { 'Z', 'S', 'P', 'R' };
	public static final byte[] ZSPR_VERSION = { 1 }; // only 1 byte, but array for future proofing
	public static final int[] CKSM_OFFSET_INDICES = // where to find the checksum in file
			new int[] { 0x05, 0x06 };
	public static final int[] SPRITE_OFFSET_INDICES = // where to find the sprite offset in file
			new int[] { 0x07, 0x08, 0x09, 0x0A };
	public static final int[] PAL_OFFSET_INDICES = // where to find the palette offset in file
			new int[] { 0x0D, 0x0E, 0x0F, 0x10 };

	// class constants
	// data sizes for sprites
	public static final int SPRITE_DATA_SIZE = 896 * 32; // 28672
	private static final short SPRITE_SIZE_SHORT = (short) SPRITE_DATA_SIZE; // cast to not get extra bytes
	public static final int PAL_DATA_SIZE = 0x78;
	private static final short PAL_SIZE_SHORT = (short) SPRITE_DATA_SIZE; // cast to not get extra bytes

	// data sizes for images
	public static final int RASTER_SIZE = 128 * 448 * 4;
	public static final int INDEXED_RASTER_SIZE = 128 * 448;

	// ROM offsets
	public static final int SPRITE_OFFSET = 0x80000;
	public static final int PAL_OFFSET = 0x0DD308;
	public static final int[] GLOVE_OFFSETS =
			new int[] { 0xDEDF5, 0xDEDF6, 0xDEDF7, 0xDEDF }; // gloves, gloves, mitts, mitts

	// format of snes 4bpp {row (r), bit plane (b)}
	// bit plane 0 indexed such that 1011 corresponds to 0123
	public static final int BPPI[][] = new int[][] {
			{0,0},{0,1},{1,0},{1,1},{2,0},{2,1},{3,0},{3,1},
			{4,0},{4,1},{5,0},{5,1},{6,0},{6,1},{7,0},{7,1},
			{0,2},{0,3},{1,2},{1,3},{2,2},{2,3},{3,2},{3,3},
			{4,2},{4,3},{5,2},{5,3},{6,2},{6,3},{7,2},{7,3}
	};

	private static final byte[][] ZAPPALETTE = new byte[][] {
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
	 * Indexes an image based on a palette.
	 * Assumes ABGR color space.
	 * <br><br>
	 * If a color matches an index that belongs to one of the latter 3 mails
	 * but does not match anything in green mail
	 * then it is treated as the color at the corresponding index of green mail.
	 * 
	 * @param pixels - aray of color indices
	 * @param pal - palette colors
	 * @return {@code byte[][][]} representing the image as a grid of color indices
	 */
	public static byte[] index(byte[] pixels, int[] pal) {
		int dis = INDEXED_RASTER_SIZE;

		// all 8x8 squares, read left to right, top to bottom
		byte[] ret = new byte[INDEXED_RASTER_SIZE];

		// read image
		for (int i = 0; i < dis; i++) {
			// get each color and get rid of sign
			// colors are stored as {A,B,G,R,A,B,G,R...}
			int b = Byte.toUnsignedInt(pixels[i*4+1]);
			int g = Byte.toUnsignedInt(pixels[i*4+2]);
			int r = Byte.toUnsignedInt(pixels[i*4+3]);

			// convert to 9 digits
			int rgb = (1000000 * r) + (1000 * g) + b;

			// find palette index of current pixel
			for (int s = 0; s < pal.length; s++) {
				if (pal[s] == rgb) {
					ret[i] = (byte) (s % 16); // mod 16 in case it reads another mail
					break;
				}
			}
		}
		return ret;
	}

	/**
	 * Turn the image into an array of 8x8 blocks.
	 * Assumes ABGR color space.
	 * <br><br>
	 * If a color matches an index that belongs to one of the latter 3 mails
	 * but does not match anything in green mail
	 * then it is treated as the color at the corresponding index of green mail.
	 * 
	 * @param pixels - aray of color indices
	 * @param pal - palette colors
	 * @return {@code byte[][][]} representing the image as a grid of color indices
	 */
	public static byte[][][] get8x8(byte[] pixels) {
		int dis = INDEXED_RASTER_SIZE;
		int largeCol = 0;
		int intRow = 0;
		int intCol = 0;
		int index = 0;

		// all 8x8 squares, read left to right, top to bottom
		byte[][][] eightbyeight = new byte[896][8][8];

		// read image
		for (int i = 0; i < dis; i++) {
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
					}
				}
			}
		}
		return eightbyeight;
	}

	/**
	 * Indexes a sprite and turns into 8x8 in one go.
	 */
	public static byte[][][] indexAnd8x8(byte[] pixels, int[] palette) {
		return get8x8(
					index(pixels, palette)
				);
	}

	/**
	 * Takes a sprite and turns it into 896 blocks of 8x8 pixels
	 * @param sprite
	 */
	public static byte[][][] makeSpr8x8(byte[] sprite) {
		byte[][][] ret = new byte[896][8][8];

		// current block we're working on, each sized 32
		// start at -1 since we're incrementing at 0mod32
		int b = -1;
		// locate where in interlacing map we're reading from
		int g;
		for (int i = 0; i < SPRITE_DATA_SIZE; i++) {
			// find interlacing index
			g = i%32;
			// increment at 0th index
			if (g == 0) {
				b++;
			}
			// row to look at
			int r = BPPI[g][0];
			// bit plane of byte
			int p = BPPI[g][1];

			// byte to unravel
			byte q = sprite[i];

			// run through the byte
			for (int c = 0; c < 8; c++) {
				// AND with 1 shifted to the correct plane
				boolean bitOn = (q & (1 << (7-c))) != 0;
				// if true, OR with that plane in index map
				if (bitOn) {
					ret[b][r][c] |= (1 << (p));
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
	public static byte[][] getPal(byte[] sprite) {
		byte[][] ret = new byte[64][3];
		int byteLoc = 1;
		for (int i = 0; i < 64; i++) {
			if (i % 16 == 0) {
				ret[0][0] = 0;
				ret[0][1] = 0;
				ret[0][2] = 0;
			} else {
				short color = 0;
				int pos = SPRITE_DATA_SIZE + (byteLoc++ * 2) - 2;
				color = (short) Byte.toUnsignedInt(sprite[pos+1]);
				color <<= 8;
				color |= (short) Byte.toUnsignedInt(sprite[pos]);

				ret[i][0] = (byte) (((color >> 0) & 0x1F) << 3);
				ret[i][1] = (byte) (((color >> 5) & 0x1F) << 3);
				ret[i][2] = (byte) (((color >> 10) & 0x1F) << 3);
			}
		}

		return ret;
	}

	public static byte[][] getSubpal(byte[][] pal, int palIndex) {
		byte[][] ret = new byte[16][3];
		int offset = palIndex * 16;
		for (int i = 0; i < 16; i++) {
				ret[i] = pal[i+offset];
		}
		return ret;
	}

	/**
	 * Turn index map in 8x8 format into an array of ABGR values
	 */
	public static byte[] makeRaster(byte[][][] ebe, byte[][] palette) {
		byte[] ret = new byte[RASTER_SIZE];
		int largeCol = 0;
		int intRow = 0;
		int intCol = 0;
		int index = 0;
		byte[] color;
		// read image
		for (int i = 0; i < RASTER_SIZE / 4; i++) {
			// get pixel color index
			byte coli = ebe[index][intRow][intCol];
			// get palette color
			color = palette[coli];
			// index 0 = trans
			if (coli == 0) {
				ret[i*4] = 0;
			}
			else {
				ret[i*4] = (byte) 255;
			}

			// BGR
			ret[i*4+1] = color[2];
			ret[i*4+2] = color[1];
			ret[i*4+3] = color[0];

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
					}
				}
			}
		}
		return ret;
	}

	/**
	 * Turns a 4 byte raster {A,B,G,R} into an integer array and sets the image.
	 * @param raster
	 * @return
	 */
	public static BufferedImage makeSheet(byte[] raster) {
		BufferedImage image = new BufferedImage(128, 448, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		int[] rgb = new int[128 * 448];
		for (int i = 0, j = 0; i < rgb.length; i++) {
			int a = raster[j++] & 0xff;
			int b = raster[j++] & 0xff;
			int g = raster[j++] & 0xff;
			int r = raster[j++] & 0xff;
			rgb[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		image.setRGB(0, 0, 128, 448, rgb, 0, 128);

		return image;
	}

	public static BufferedImage[] makeAllMails(byte[][][] eightbyeight, byte[][] pal) {
		BufferedImage[] ret = new BufferedImage[5];
		byte[][] subpal;
		byte[] raster;
		for (int i = 0; i < 4; i++) {
			subpal = getSubpal(pal, i);
			raster = makeRaster(eightbyeight,subpal);
			ret[i] = makeSheet(raster);
		}

		raster = makeRaster(eightbyeight,ZAPPALETTE);
		ret[4] = makeSheet(raster);

		return ret;
	}

	public static void patchRom(byte[] sprData, byte[] palData, byte[] glovesData,
			String romTarget) throws IOException, FileNotFoundException {
		// Acquire ROM data
		byte[] rom_patch;
		FileInputStream fsInput = new FileInputStream(romTarget);
		rom_patch = new byte[(int) fsInput.getChannel().size()];
		fsInput.read(rom_patch);
		fsInput.getChannel().position(0);
		fsInput.close();

		// filestream save .spr file to ROM
		FileOutputStream fsOut = new FileOutputStream(romTarget);

		for(int i = 0; i < SPRITE_DATA_SIZE; i++) {
			rom_patch[SPRITE_OFFSET + i] = sprData[i];
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
				rom_patch[GLOVE_OFFSETS[i]] = glovesData[i];
			}
		}

		// add palette data to ROM
		for (int i = 0; i < PAL_DATA_SIZE; i++) {
			rom_patch[PAL_OFFSET + i] = palData[i];
		}

		fsOut.write(rom_patch, 0, rom_patch.length);

		fsOut.close();
	}

	/**
	 * Reads a ROM to create a SPR file data stream.
	 * @param romPath
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] getSprFromRomName(String romPath) throws IOException, FileNotFoundException {
		byte[] ROM = readFile(romPath);
		byte[] ret = getSprFromRom(ROM);

		return ret;
	}

	/**
	 * Reads a ROM to create a SPR file data stream.
	 * @param ROM
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static byte[] getSprFromRom(byte[] ROM) throws IOException {
		byte[] ret = new byte[SPRITE_DATA_SIZE+PAL_DATA_SIZE];

		for (int i = 0; i < SPRITE_DATA_SIZE; i++) {
			ret[i] = ROM[SPRITE_OFFSET + i];
		}

		for (int i = 0; i < PAL_DATA_SIZE; i++) {
			ret[i+SPRITE_DATA_SIZE] = ROM[PAL_OFFSET + i];
		}

		return ret;
	}

	/**
	 * Converts an index map into a proper 4BPP (SNES) byte map.
	 * @param eightbyeight - color index map
	 * @return new byte array in SNES4BPP format
	 */
	public static byte[] exportToSPR(byte[][][] eightbyeight) {

		// format of SNES 4bpp {row (r), bit plane (b)}
		// bit plane 0 indexed such that 1011 corresponds to 0123
		int bppi[][] = {
				{0,0},{0,1},{1,0},{1,1},{2,0},{2,1},{3,0},{3,1},
				{4,0},{4,1},{5,0},{5,1},{6,0},{6,1},{7,0},{7,1},
				{0,2},{0,3},{1,2},{1,3},{2,2},{2,3},{3,2},{3,3},
				{4,2},{4,3},{5,2},{5,3},{6,2},{6,3},{7,2},{7,3}
		};

		// bit map
		boolean[][][] fourbpp = new boolean[896][32][8];

		for (int i = 0; i < fourbpp.length; i++) {
			// each byte, as per bppi
			for (int j = 0; j < fourbpp[0].length; j++) {
				for (int k = 0; k < 8; k++) {
					// get row r's bth bit plane, based on index j of bppi
					int row = bppi[j][0];
					int plane = bppi[j][1];
					int byteX = eightbyeight[i][row][k];
					// AND the bits with 1000, 0100, 0010, 0001 to get bit in that location
					boolean bitB = ( byteX & (1 << plane) ) > 0;
					fourbpp[i][j][k] = bitB;
				}
			}
		}

		// byte map
		// includes the size of the sheet (896*32) + palette data (0x78)
		byte[] bytemap = new byte[896*32];

		int k = 0;
		for (int i = 0; i < fourbpp.length; i++) {
			for (int j = 0; j < fourbpp[0].length; j++) {
				byte next = 0;
				// turn true false into byte
				for (boolean a : fourbpp[i][j]) {
					next <<= 1;
					next |= (a ? 1 : 0);
				}
				bytemap[k] = next;
				k++;
			}
		}
		// end 4BPP

		return bytemap;
	}

	/**
	 * Create binary palette data for appending to the end of the {@code .spr} file.
	 * @param pal - 64/66 length{@code int[]} contained the palette colors as RRRGGGBBB
	 * @return <b>byte[]<b> containing palette data in 5:5:5 format
	 */
	public static byte[] getPalDataFromArray(int[] pal) {
		// create palette data as 5:5:5
		ByteBuffer palRet = ByteBuffer.allocate(0x78);

		for (int i = 1; i < 16; i++) {
			for (int t = 0; t < 4; t++) {
				int r = pal[i+16*t] / 1000000;
				int g = (pal[i+16*t] % 1000000) / 1000;
				int b = pal[i+16*t] % 1000;
				short s = (short) ((( b / 8) << 10) | ((( g / 8) << 5) | ((( r / 8) << 0))));
				// put color into every mail palette
				palRet.putShort(30*t+((i-1)*2),Short.reverseBytes(s));
			}
		}

		// end palette
		return palRet.array();
	}

	/**
	 * Finds binary gloves data from the last 2 indices of the palette.
	 * If the palette has no gloves data (it is only 64 colors), return null data.
	 */
	public static byte[] getGlovesDataFromArray(int[] pal) {
		int l = pal.length;
		assert l == 64 || l == 66;
		if (l != 66) {
			return new byte[] { 0, 0, 0, 0};
		}

		ByteBuffer palRet = ByteBuffer.allocate(4);
		int palI = 64;
		for (int i = 0; i < 2; i++, palI++) {
				int r = pal[palI] / 1000000;
				int g = (pal[palI] % 1000000) / 1000;
				int b = pal[palI] % 1000;
				short s = (short) ((( b / 8) << 10) | ((( g / 8) << 5) | ((( r / 8) << 0))));
				// put color
				palRet.putShort(i*2,Short.reverseBytes(s));
		}

		// end palette
		return palRet.array();
	}

	/**
	 * Takes every color in a palette and rounds each byte to the nearest 8.
	 * @param pal - palette to round
	 */
	public static int[] roundPalette(int[] pal) {
		int[] ret = new int[pal.length];
		for (int i = 0; i < pal.length; i++) {
			int color = pal[i];
			int r = color / 1000000;
			int g = (color % 1000000) / 1000;
			int b = color % 1000;
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
	 * Writes the image to an {@code .spr} file.
	 * @param map - SNES 4BPP file, including 5:5:5
	 * @param loc - File path of exported sprite
	 */
	public static void writeFile(byte[] map, String loc) throws IOException {
		// create a file at directory
		new File(loc);

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

	// TODO : ALL THESE
	/**
	 * Reads palette properly from last area of sprite file
	 * @param curSprite
	 * @return
	 */
	public static byte[] getPaletteFromSPR(byte[] curSprite) {
		
		return null;
	}

	/**
	 * 
	 */
	public static byte[] writeChecksum(byte[] spr) {
		return null;
	}

	/**
	 * Should always return true if it works.
	 * @param spr
	 * @return
	 * @throws BadChecksumException
	 */
	public static boolean runChecksum(byte[] spr) throws BadChecksumException {
		// check sum whatever
//		if (badSum) {
//			throw new BadChecksumException();
//		}
		return true;
	}

	/**
	 * 
	 */
	public static byte[] findSpriteData(byte[] spr) {
		return null;
	}
	
	/**
	 * 
	 */
	public static byte[] findPaletteData(byte[] spr) {
		return null;
	}

	/**
	 * 
	 */
	public static byte[] makeSPRFile(byte[] sprData, byte[] palData, byte[] glovesData,
			String sprName, String author) {
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
		byte[] sName = toByteArray(sprName + "\0"); // add null terminators here
		byte[] auth = toByteArray(author + "\0");

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
		for (byte b : sprData) { // Size defined in SPRITE_DATA_SIZE
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
		byte[] ret2 = new byte[s];
		for (int i = 0; i < s; i++) {
			ret2[i] = (Byte) ret.get(i);
		}

		// calculate checksum
		byte[] cksm = writeChecksum(ret2);

		// add checksum to file
		for (int i = 0; i < CKSM_OFFSET_INDICES.length; i++) {
			ret.set(CKSM_OFFSET_INDICES[i], cksm[i]);
		}

		return ret2;
	}

	/**
	 * {@code makeSPRFile} without an author or sprite name
	 */
	public static byte[] makeSPRFile(byte[] sprData, byte[] palData, byte[] glovesDatas) {
		return makeSPRFile(sprData, palData, glovesDatas, "", "");
	}
	
	/**
	 * {@code makeSPRFile} without an author
	 */
	public static byte[] makeSPRFile(byte[] sprData, byte[] palData, byte[] glovesDatas,
			String sprName) {
		return makeSPRFile(sprData, palData, glovesDatas, sprName, "");
	}
}