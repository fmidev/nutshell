package nutshell10;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Echo {

	public Echo(){



	}

	public BufferedImage readImage(String filename){

		BufferedImage image = null;

		try {
			java.net.URL r = getClass().getClassLoader().getResource(filename);
			System.out.println(r);
			image = ImageIO.read(r);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return image;
	}


	/**
	 * @param arg Input
	 */
	public static void main (String[] arg) {

		String filename = "resources/nutshell-logo.png";
		if (arg.length > 0)
			filename = arg[0];
		System.out.println(filename);

		Echo echo = new Echo();

		echo.readImage(filename);



	}

}
