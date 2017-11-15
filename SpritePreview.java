package SpriteManipulator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileView;

public class SpritePreview extends FileView {
	private static BufferedImage PALETTE_ICON; {
		try {
			PALETTE_ICON = ImageIO.read(SpritePreview.class.getResourceAsStream(
					"images/fileicon-palette.png"));
		} catch (IOException e) {
	}};

	private static BufferedImage ROM_ICON; {
		try {
			ROM_ICON = ImageIO.read(SpritePreview.class.getResourceAsStream(
					"images/fileicon-rom.png"));
		} catch (IOException e) {
	}};

	public Icon getIcon(File f) {
		String path = f.getAbsolutePath();
		ZSPRFile spr;
		BufferedImage preview;
		if (SpriteManipulator.testFileType(path, ZSPRFile.EXTENSION)) {
			try {
				spr = ZSPRFile.readFile(path);
			} catch (IOException
					| ZSPRFormatException e) {
				return new ImageIcon();
			}

			byte[] spriteData = spr.getSpriteData();
			byte[][][] ebe = SpriteManipulator.makeSpr8x8(spriteData);

			// check for an empty head
			boolean emptyHead = true;
			checkHead:
			for (int i = 0; i < 2; i++) {
				int index = new int[] {2,18}[i]; // blocks that hold head data
				int pos = index * SpriteManipulator.SPRITE_BLOCK_SIZE;
				for (int j = 0; j < SpriteManipulator.SPRITE_BLOCK_SIZE * 2; j++, pos++) {
					if (spriteData[pos] != 0) {
						emptyHead = false;
						break checkHead;
					}
				}
			}
			byte[][] pal = SpriteManipulator.getPal(spr.getPalData());
			BufferedImage sheet = SpriteManipulator.makeSheet(SpriteManipulator.makeRaster(ebe, pal));

			// if empty, use cell B3
			// otherwise use A1
			int x = emptyHead ? 48 : 16;
			int y = emptyHead ? 16 : 0;
			preview = sheet.getSubimage(x, y, 16, 16);

			//return new ImageIcon(preview);
			return new ImageIcon(preview);
		} else if (SpriteManipulator.testFileType(path, new String[]{ "gpl", "pal", "txt" })) {
			return new ImageIcon(PALETTE_ICON);
		} else if (SpriteManipulator.testFileType(path, "sfc")) {
			return new ImageIcon(ROM_ICON);
		} else { // non zspr files can use their default icons
			return super.getIcon(f);
		}
	}
}