import java.net.*;

public class PlayList {
	public String fileName;
	private URL fileDirect;
	private int Index;
	
	private PlayList next;
	private PlayList prev;
	
	private final static PlayList head = new PlayList();
	private final static PlayList tail = new PlayList();

	public PlayList(){
		fileName = null;
		fileDirect = null;
		next = null;
		prev = null;
	}
	
	public PlayList(String f_name, URL f_dec){
		fileName = f_name;
		fileDirect = f_dec;
	}
	
	public void Init(){
		head.next = tail;
		head.prev = null;
		tail.next = null;
		tail.prev = head;
	}
	
	public void Addfile(String f_name, URL f_dec){
		PlayList temp = new PlayList(f_name, f_dec);
		tail.prev.next = temp;
		temp.prev = tail.prev;
		temp.next = tail;
		tail.prev = temp;
		setIndex();;
	}
	
	public void Delfile(int index){
			PlayList temp = head.next;
			while(temp != null){
				if(temp.Index == index){
					temp.prev.next = temp.next;
					temp.next.prev = temp.prev;
					temp = null;
					break;
				}
				temp = temp.next;
			}
			setIndex();
	}
	
	public int getSize(){
		int count = 0;
		PlayList temp = head.next;
		while(temp != null){
			count++;
			temp = temp.next;
		}
		return count;
	}
	
	public String[] getArr(){
		PlayList temp = head.next;
		String[] arr = new String[getSize()];
		for(int i = 0; i < getSize(); i++){
			arr[i] = temp.getName(i);
			temp = temp.next;
		}
		return arr;
	}
	
	private void setIndex(){
		int i = 0;
		PlayList temp = head.next;
		while(temp.next != null){
			temp.Index = i++;
			temp = temp.next;
		}
	}
	
	public URL getURL(int i){
		URL url = null;
		PlayList temp = head.next;
		while(temp.next != null){
			if(temp.Index == i){
				url = temp.fileDirect;
				return url;
			}
			temp = temp.next;
		}
		return url;
	}
	
	public String getName(int i){
		String name = null;
		PlayList temp = head.next;
		while(temp != tail){
			if(temp.Index == i){
				name = temp.fileName;
				return name;
			}
			temp = temp.next;
		}
		return name;
	}
}
