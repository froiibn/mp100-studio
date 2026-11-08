import javax.media.Player;
import javax.media.Time;
import javax.swing.*;

public class Progress extends Thread{
	Time mTime;
	Time tTime;
	long currentTime, temp;
	Player tar;
	JProgressBar bar;
	
	Progress(Player pl, JProgressBar bar){
		tar = pl;
		this.bar = bar;
	}
	public void run(){
		mTime = tar.getMediaTime();
		temp = System.currentTimeMillis();
        try{
        	while(true){
        		currentTime = System.currentTimeMillis();
        		tTime = new Time((currentTime - temp));
        		double tick = tTime.getSeconds()/mTime.getSeconds() * 100;
        		bar.setValue((int)tick);
        		currentTime = 0;
        		Thread.sleep(1000);
        	}
        }catch(InterruptedException r){}
	}
}
