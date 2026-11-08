import javax.swing.*;

public class Timer extends Thread{
	JTextField txt;
	private boolean flag = false;
	Timer(JTextField txt){
		this.txt = txt;
	}
	
    public void run() {
        int min = 0, sec = 0, time = 0;
        flag = false;
        try{
        	while(!flag){
        		Thread.sleep(1000);
        		min = time /60;
        		sec = time % 60;
        		String st = min +":"+ sec;
        		txt.setText(st);
        		time += 1;
        	}
        }catch(InterruptedException r){}
    }
    
    public void stopp(){
    	flag = true;
    }
}
