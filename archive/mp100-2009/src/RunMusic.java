import java.awt.*;
import java.io.IOException;
import java.net.*;
import javax.media.*;
import java.applet.*;
import javax.swing.*;

public class RunMusic extends Applet implements ControllerListener{
	Player player = null;
	JProgressBar th;
	RunMusic(JProgressBar a){
		th = a;
	}
	public void play(URL hh){
		try{
			player = Manager.createPlayer(hh);
			player.addControllerListener(this);
			player.start();
			Progress prog = new Progress(player, th);
			prog.start();
		}catch(NoPlayerException e){
			e.printStackTrace();
		}catch(MalformedURLException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	public void stop(){
		player.stop();
		player.deallocate();
	}

	public void controllerUpdate(ControllerEvent e) {
		if(e instanceof RealizeCompleteEvent){
			Component component;
			if((component = player.getVisualComponent()) != null)
				add("Center", component);
			if((component = player.getControlPanelComponent()) != null)
				add("South", component);
			validate();
		}
	}
}
