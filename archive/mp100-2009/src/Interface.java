import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.MalformedURLException;

import javax.media.Time;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.util.*;

class Interface extends JFrame implements ActionListener{
	private static final long serialVersionUID = 1L;
	private RunMusic run;
	private JFileChooser fileChooser;
	private JButton play;
	private JButton stop;
	private JButton add;
	private JButton del;
	private JTextField textBox;
	private JTextField timeTable;
	private DefaultTableModel model;
	private JTable table;
	private JScrollPane scroll;
    File files = null;
    PlayList pList;
    Boolean state;
    JProgressBar prog;
    Timer time;
	public Interface(){
		play = new JButton(new ImageIcon("play.gif"));
		stop = new JButton(new ImageIcon("stop.gif"));
		add = new JButton(new ImageIcon("add.gif"));
		del = new JButton(new ImageIcon("del.gif"));
		textBox = new JTextField();
		textBox.setEditable(false);
		timeTable = new JTextField();
		timeTable.setEditable(false);
		this.setLayout(null);
		String arr[] = {"Play List"};
		model = new DefaultTableModel(arr, 0);
		table = new JTable(model);
		table.setShowHorizontalLines(false);
		scroll = new JScrollPane(table);
		pList = new PlayList();
		prog = new JProgressBar(0, 100);
		pList.Init();
		state = false;
		play.setBounds(90, 45, 43, 45);
		stop.setBounds(135, 45, 43, 45);
		add.setBounds(30, 55, 56, 29);
		del.setBounds(180, 55, 56, 29);
		textBox.setBounds(10, 10, 200, 15);
		timeTable.setBounds(220, 10, 40, 15);
		scroll.setBounds(10, 95, 250, 180);
		prog.setBounds(10, 30, 250, 10);
		play.setBackground(Color.black);
		stop.setBackground(Color.black);
		textBox.setBackground(Color.black);
		textBox.setText("Music Player Ver.1.00");
		textBox.setForeground(Color.yellow);
		timeTable.setText("00:00");
		timeTable.setBackground(Color.black);
		timeTable.setForeground(Color.yellow);
		table.setBackground(Color.black);
		table.setForeground(Color.LIGHT_GRAY);
		table.setSelectionBackground(Color.DARK_GRAY);
		table.setSelectionForeground(Color.white);
		scroll.getViewport().setBackground(Color.black);
		prog.setBackground(Color.BLACK);
		prog.setForeground(Color.ORANGE);
		Container ct = getContentPane();
		ct.add(play);
		ct.add(stop);
		ct.add(add);
		ct.add(del);
		ct.add(textBox);
		ct.add(timeTable);
		ct.add(scroll);
		ct.add(prog);
		ct.setBackground(Color.black);
		play.addActionListener(this);
		stop.addActionListener(this);
		add.addActionListener(this);
		del.addActionListener(this);
		time = new Timer(timeTable);
		run = new RunMusic(prog);
	}

	public void actionPerformed(ActionEvent e){
		if(e.getSource() == play){
			if(pList.getURL(table.getSelectedRow()) == null)
				textBox.setText("File is not selected");
			else{
				if(state == false){
					textBox.setText(pList.getName(table.getSelectedRow()));
					run.play(pList.getURL(table.getSelectedRow()));
					state = true;
					time.start();
					System.out.println();
				}
				else{
					run.stop();
					run.play(pList.getURL(table.getSelectedRow()));
					textBox.setText(pList.getName(table.getSelectedRow()));
				}
			}
		}
		else if(e.getSource() == stop){
			if(state == true){
				textBox.setText("Music Player Ver.1.00");
				run.stop();
				timeTable.setText("00:00");
				state = false;
				try{
					Thread.sleep(1000);
				}catch(Exception a){}
				time.interrupt();
			}
			else
				textBox.setText("Nothing for stop");
		}
		else if(e.getSource() == add){
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("MP3","mp3"));
			int value = fileChooser.showOpenDialog(this);
			if (value == JFileChooser.APPROVE_OPTION){
				files = fileChooser.getSelectedFile();
            }
			else
				return;
			try{
				pList.Addfile(files.getName(), files.toURL());
			}catch (MalformedURLException e1){
				e1.printStackTrace();
			}
			String arr[] = new String[1];
			arr[0] = files.getName();
			model.addRow(arr);
		}
		
		else if(e.getSource() == del){
			if(pList.getName(table.getSelectedRow()) != null){
				pList.Delfile(table.getSelectedRow());
				model.removeRow(table.getSelectedRow());
			}
			else
				textBox.setText("File is not selected");
		}
	}	
}


