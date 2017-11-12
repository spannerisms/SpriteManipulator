package SpriteManipulator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileView;

public class SpritePreview extends FileView {
	public Icon getIcon(File f) {
		String path = f.getAbsolutePath();
		ZSPRFile spr;
		BufferedImage preview;
		if (SpriteManipulator.testFileType(path, ZSPRFile.EXTENSION)) {
			try {
				spr = ZSPRFile.readFile(path);
			} catch (IOException | NotZSPRException | ObsoleteSPRFormatException | BadChecksumException e) {
				return new ImageIcon();
			}
			byte[][][] ebe = SpriteManipulator.makeSpr8x8(spr.getSpriteData());
			byte[][] pal = SpriteManipulator.getPal(spr.getPalData());
			BufferedImage sheet = SpriteManipulator.makeSheet(SpriteManipulator.makeRaster(ebe, pal));
			preview = sheet.getSubimage(16, 0, 16, 16);
			return new ImageIcon(preview);
		} else {
			return super.getIcon(f);
		}
	}
}