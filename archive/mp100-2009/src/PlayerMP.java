import javax.media.NoPlayerException;
import javax.swing.*;
import java.io.IOException;
import java.net.MalformedURLException;

public class PlayerMP{
	public static void main(String args[]) throws NoPlayerException, MalformedURLException, IOException{
		Interface player = new Interface();
		player.setTitle("MP-100");
		player.setSize(280, 330);
		player.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		player.setVisible(true);
		player.setResizable(false);
	}
}
