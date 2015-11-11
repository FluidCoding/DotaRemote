package com.example.dotaremote;

import java.io.*;
import java.net.*;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class DotaRemote extends Activity implements Callback {

	// On Screen Components
	private EditText serverIp;
	private Button connectPhones;
	private Button launchGame, findGame, pauseGame, exitDota, autoAccept;
	private Button sendLobbyMsg, sendTeamMsg, sendAllMsg, sendServerMsg, clrMsg;
	private EditText messageBox;
	private TextView conStatus;
	private ToggleButton toggleBStream;
	private ImageView lobbyChatView;
	// Objects for net i/o
	private String serverIpAddress = "";
	Thread serverComThread;
	public DotaRemote drHandler;
	private Socket socket;
	// Command Constants \\
	final int START_DOTA = 1;
	final int AUTO_ACCEPT = 2;
	final int FIND_GAME = 3;
	final int PAUSE_GAME = 4;
	final int MESSAGE_LOBBY = 5;
	final int MESSAGE_TEAM = 6;
	final int MESSAGE_ALLCHAT = 7;
	final int EXIT_DOTA = 8;
	final int SERVER_CONNECT = 0;
	final int SERVER_DISCONNECT = 10;
	final int STREAM_PIC = 9;
	// Port used for now...
	final int PORT = 8095;
	// Flags
	private boolean connected = false;
	boolean imgStreamActive = false;

	Handler handler = new Handler();
	public Runnable runny = new Runnable(){ // This thread runs in the UI
		public void run() {
			Toast.makeText(getApplicationContext(), "Dota 2 Started.", Toast.LENGTH_LONG).show();
		}
	};
	Handler msgHandle = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case START_DOTA:
				Toast.makeText(getApplicationContext(), "Dota 2 Started.", Toast.LENGTH_LONG).show();
				break;
			case SERVER_CONNECT:
				Toast.makeText(getApplicationContext(), "Client Connected.", Toast.LENGTH_SHORT).show();
				setConnectionStatus(true);
				break;
			case SERVER_DISCONNECT:
				Toast.makeText(getApplicationContext(), "Client Disconnected.", Toast.LENGTH_SHORT).show();
				setConnectionStatus(false);
				break;
			case STREAM_PIC:
				if(!imgStreamActive){
					Toast.makeText(getApplicationContext(), "Stream Initialized.", Toast.LENGTH_SHORT).show();
					imgStreamActive=true;
				}
				Bundle b = msg.getData();
				Bitmap bim = (Bitmap)b.get("Image");
				if(bim!=null){
					Toast.makeText(getApplicationContext(), "Img Recieved", Toast.LENGTH_SHORT).show();
					lobbyChatView.setImageBitmap(bim);
				}
				else{
					try {
						bim = BitmapFactory.decodeStream(new FileInputStream("cache.png"));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				break;
			default:
				break;
			}
			super.handleMessage(msg);
		}};

		@Override
		public boolean handleMessage(Message msg) {

			return false;
		}

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_dota_remote);

			//dRhandler = this;
			// Init Components  
			serverIp = (EditText) findViewById(R.id.editText1);
			conStatus = (TextView)findViewById(R.id.textViewConStatusValue);
			connectPhones = (Button) findViewById(R.id.bConnect);
			launchGame = (Button)findViewById(R.id.button_launchDota);
			messageBox = (EditText)findViewById(R.id.editTextMsg);
			findGame = (Button)findViewById(R.id.buttonFind);
			pauseGame = (Button)findViewById(R.id.buttonPause);
			sendLobbyMsg = (Button)findViewById(R.id.buttonMsgLobby);
			sendTeamMsg = (Button)findViewById(R.id.buttonMsgTeam);
			sendAllMsg = (Button)findViewById(R.id.buttonMsgAll);
			sendServerMsg = (Button)findViewById(R.id.buttonMsgServer);
			exitDota = (Button)findViewById(R.id.buttonExitDota);
			clrMsg = (Button)findViewById(R.id.buttonClear);
			autoAccept = (Button)findViewById(R.id.buttonAutoAccept);
			toggleBStream = (ToggleButton)findViewById(R.id.toggleButtonLobbyStream);
			lobbyChatView = (ImageView)findViewById(R.id.imageViewLobbyChat);


			// ---------------------------------
			// Listeners For Component Events
			// ---------------------------------

			// Connect Button Listener
			connectPhones.setOnClickListener(connectListener);

			findGame.setOnClickListener(new OnClickListener(){
				public void onClick(View v) {
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("03");
					}
					else{
						ClientThread.sendMessage("03");
					}	
				}
			});

			// Toggle Lobby Chat Stream
			toggleBStream.setOnCheckedChangeListener(new OnCheckedChangeListener(){
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					Thread chatStreamThr = null;
					if(isChecked){
						if(serverComThread==null){
							serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
							serverComThread.start();
						}
						ClientThread.sendMessage("09");
						chatStreamThr = new Thread(new LobbyChatThread(serverIp.getText().toString(),msgHandle));
						File f = new File(getApplicationContext().getFilesDir().getPath().toString()+"/cache.png");
						FileOutputStream fOut;
						try {
							fOut = new FileOutputStream(f);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
						//chatStreamThr.start();
					}
					else{
						if(chatStreamThr!=null)
							chatStreamThr.interrupt();
					}	
				}
			});

			// Auto Accept Match (USING AS DEBUG Action button)
			autoAccept.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("02");
					}
					else{
						ClientThread.sendMessage("02");
					}	
					//lobbyChatView.setImageBitmap(BitmapFactory.decodeFile("cache.png"));
				}
			});

			// Clears Chat Message Box
			clrMsg.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					messageBox.setText("");
				}
			});

			// Exit Dota if Confirmed
			exitDota.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					// Show Confirmation dialog 
					showConfirmationDialog();
				}
			});

			pauseGame.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("04");
					}
					else{
						ClientThread.sendMessage("04");
					}	
				}
			});

			sendServerMsg.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(),msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("00" + messageBox.getText().toString());
					}
					else{
						ClientThread.sendMessage("00" + messageBox.getText().toString());
					}
				}
			});

			sendLobbyMsg.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("05"+messageBox.getText().toString());
					}
					else{
						ClientThread.sendMessage("05"+messageBox.getText().toString());
					}
				}

			});

			sendTeamMsg.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread!=null){
						ClientThread.sendMessage("06"+messageBox.getText().toString());
					}
					else{
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("06"+messageBox.getText().toString());
					}
				}
			});

			sendAllMsg.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle, socket));
						serverComThread.start();
						ClientThread.sendMessage("07"+messageBox.getText().toString());
					}
					else{
						ClientThread.sendMessage("07"+messageBox.getText().toString());
					}
				}
			});

			launchGame.setOnClickListener(new OnClickListener(){
				@Override 
				public void onClick(View v){
					if(serverComThread==null){
						serverComThread = new Thread(new ClientThread(serverIp.getText().toString(), msgHandle,socket));
						serverComThread.start();
						ClientThread.sendMessage("01");
					}
					else{
						ClientThread.sendMessage("01");
					}
				}
			});


		}

		/*
		 * Send exit dota command
		 */
		public void exitDota(){
			if(serverComThread!=null){
				ClientThread.sendMessage("08");
			}
			else{
				serverComThread = new Thread(new ClientThread(serverIp.getText().toString(),msgHandle,socket));
				serverComThread.start();
				ClientThread.sendMessage("08");
			}
		}


		boolean connectSocket(){
			try {
				// Socket Details
				socket = new Socket();
				SocketAddress adr = new InetSocketAddress(serverIp.getText().toString(), PORT);
				socket.connect(adr, 1500);
			}catch (SocketTimeoutException e) {
				System.err.println(" Error Connecting: \n" + e);
				return false;
			} catch (UnknownHostException e) {		
				System.err.println(" UnknownHostExceptiont \n" + e);
				return false;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}catch (Exception e){
				e.printStackTrace();
				return false;
			}
			return true;
		}

		/*
		 * Show confirmation dialog and exit dota
		 */
		void showConfirmationDialog(){
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.exit_dota);
			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface dialog, int which) {
					exitDota();
				}});
			builder.setNegativeButton("No", new  DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.show();
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			// Inflate the menu; this adds items to the action bar if it is present.
			getMenuInflater().inflate(R.menu.activity_dota_remote, menu);
			return true;
		}

		public void setConnectionStatus(boolean status){
			if(status){
				conStatus.setText("Connected");
				conStatus.setTextAppearance(getApplicationContext(), R.style.TextConnected);
			}
			else{
				conStatus.setText("Not Connected");
				conStatus.setTextAppearance(getApplicationContext(), R.style.TextDisconnected);
			}
		}
		/**
		 * Listener For Connect press event
		 * should establish a connection with local client
		 * spawning a thread to manage this connection
		 */
		OnClickListener connectListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				//if (!connected) {// Use member function
				serverIpAddress = serverIp.getText().toString();
				if(connectSocket()){
					setConnectionStatus(true);
				}
				else{
					setConnectionStatus(false);
				}
				Thread ct = new Thread(new ClientThread("00", serverIpAddress, msgHandle, socket));
				ct.start();
				/*
				if (!serverIpAddress.equals("")) {
					Thread cThread = new Thread(new ClientThread(serverIp.getText().toString(), handler));
					cThread.start();
				}
				 */
				//}
			}
		};
}

/**
 * This Class handles streaming a png image from server
 * and passes the image to main class to be displayed.
 */
class LobbyChatThread implements Runnable {
	Handler h;
	Socket socket;
	DataInputStream in;
	String serverAddy;
	final int PORT = 8094;
	private boolean isActive = false;
	BufferedReader buffR;
	public LobbyChatThread(String serverAddress, Handler dr, Socket s)
	{
		h = dr;
		socket = s;
		serverAddy = serverAddress;
		isActive = true;
	}
	public LobbyChatThread(String serverAddress, Handler dr)
	{
		h = dr;
		serverAddy = serverAddress;
		isActive = true;
	}
	static public byte[] object2Bytes( Object o ){
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream( baos );
			oos.writeObject( o );
			return baos.toByteArray();
		}catch(IOException e){e.printStackTrace();}
		return null;
	}

	@Override
	public void run() {
		in = null;
		DataInputStream dis=null;
		Drawable d;
		Bitmap bim=null;
		socket = new Socket();
		SocketAddress adr = new InetSocketAddress(serverAddy, PORT);
		try {
			System.err.println(" Attempt Connect: \n");
			socket.connect(adr, 15000);
			isActive=true;
			dis = new DataInputStream(socket.getInputStream());
			buffR = new BufferedReader(new InputStreamReader(dis));
		} catch (SocketTimeoutException e) {
			System.err.println(" Error Connecting: \n" + e);
			isActive = false;
		} catch (UnknownHostException e) {		
			System.err.println(" UnknownHostExceptiont \n" + e);
			isActive=false;
		} catch (IOException e) {
			e.printStackTrace();
		}catch (Exception e){
			e.printStackTrace();
		}
		/*while(!socket.isConnected()){
			try {
				socket.connect(adr, 1500);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		 */
		byte[] dataBuf=null;
		long sizeOfImg = 0;
		while(isActive){
			while(sizeOfImg<1){
				try {
					//sizeOfImg = Long.parseLong(dis.readLine());
					sizeOfImg = Long.parseLong(buffR.readLine());
					Log.d("LOBBYCTHREA", Long.toString(sizeOfImg));
				} catch (IOException e) {
					e.printStackTrace();
					isActive=false;
				} catch(NumberFormatException e1){
					e1.printStackTrace();
					sizeOfImg=0;
				}
			}
			Log.d("LOBBYCTHREA", "Looking for pic...");
			dataBuf = new byte[(int) sizeOfImg];
			try {
				//buffR.read(dataBuf);
				dis.read(dataBuf);
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			//bim.compress(CompressFormat.PNG,0 , socket.getOutputStream());          
			/*try {

				//  bim = BitmapFactory.decodeByteArray()//Drawable.createFromStream(socket.getInputStream(), "Image");
				//bim = BitmapFactory.decodeStream(socket.getInputStream());
				//dis = new DataInputStream(socket.getInputStream());
				bim = BitmapFactory.decodeStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			 */
			if(dataBuf.length>1){
				// Prepare Message for Activities Thread
				//bim = BitmapFactory.decodeByteArray(dataBuf, 0, dataBuf.length);
				Message m = new Message();
				m.what = 9;
				Bundle b = new Bundle();
				b.putByteArray("Image", dataBuf);
				m.setData(b);

				// Write data to file

				try {
					File f = new File("cache.png");
					if(f.canWrite()){
						OutputStream  fOut = new FileOutputStream("cache.png");
						fOut.write(dataBuf);
						fOut.close();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

				h.sendMessage(m);
				sizeOfImg=0;
			}
		}
		// Sleep Thread for a bit...
		try{
			Thread.sleep(3000);
		}catch(InterruptedException e){e.printStackTrace();}
	}
}

/**
 * Threaded class to handle network communication
 * 
 */
class ClientThread implements Runnable {
	Callback callback;
	Socket socket;
	DataInputStream in;
	static DataOutputStream out;
	String serverAddy;
	String action;
	int PORT = 8095;
	String defaultAddr = "192.168.1.33";
	Handler drHandle;
	TextView tvConnectStatus;
	BufferedReader buffR;
	static PrintStream pOut;

	public ClientThread(String action_, String serverAddy_, Handler dr, Socket s){
		action = action_;
		serverAddy = serverAddy_;
		drHandle = dr;
		socket = s;
	}


	public ClientThread(String serverAddy_, Handler dr, Socket s){
		action = "00";
		serverAddy = serverAddy_;
		drHandle = dr;
		socket = s;
	}

	public ClientThread(String action_, String serverAddy_, Handler dr)
	{
		action = action_;
		serverAddy = serverAddy_;
		drHandle = dr;
	}

	public ClientThread(String serverAddy_, Handler dr){
		action = "Connect";
		serverAddy = serverAddy_;
		drHandle = dr;
	}

	public ClientThread(Handler dr)
	{
		action="Conenct";
		serverAddy = defaultAddr;
		drHandle = dr;
	}

	// Default Constructor
	public ClientThread(){
		action="Connect";
		serverAddy=defaultAddr;
		drHandle = null;
	}

	
	public void connect(){
		
		
	}
	
	public void run() {
		//socket = null;
		in = null;
		out = null;
		System.out.println("Client Thread Running...");
		Log.d("CTHREAD", "Client Thread Running...");
		try {
				// Socket Details
				if(socket.isConnected()==false){
					socket = new Socket();
					SocketAddress adr = new InetSocketAddress(serverAddy, PORT);
					socket.connect(adr, 1500);
					Message m = new Message();
					m.what = 0;
					drHandle.sendMessage(m);
				}
				// Update connection status on UI
				/*if(drHandle!=null){
				drHandle.setConnectionStatus(true);
			}
				 */
				// Create IO streams for communication
				out = new DataOutputStream(socket.getOutputStream());
				in = new DataInputStream(socket.getInputStream());
				out.flush();
				buffR = new BufferedReader(new InputStreamReader(in));
				pOut = new PrintStream(socket.getOutputStream());
			} catch (SocketTimeoutException e) {
				System.err.println(" Error Connecting: \n" + e);
			} catch (UnknownHostException e) {		
				System.err.println(" UnknownHostExceptiont \n" + e);
				System.exit(1);
			} catch (IOException e) {
				e.printStackTrace();
			}catch (Exception e){
				e.printStackTrace();
			}

			Log.d("CTHREAD", "Connect attempted");

			new Thread(new Runnable(){
				@Override
				public void run() {
					int action = 0;
					String buff = "";
					while(buffR!=null){
						try {
							Log.d("CTHREAD", "Listening");
							buff = buffR.readLine();

							if(buff!=null && !buff.equals("")){
								try{
									action = Integer.parseInt(buff.substring(0,2));
								}catch(NumberFormatException nfe){
									nfe.printStackTrace();
									action = 0;
								}
								Log.d("CTHRE", buff);
								switch(action){
								case(1):
									Message m = new Message();
								m.what = 1;
								drHandle.sendMessage(m);
								Log.d("CTHREAD", "Dota 2 Started.");

								/*new Thread(new Runnable() {
									@Override
									public void run()
									{
									    Toast.makeText(drHandle.getApplicationContext(), "Dota 2 Started." , Toast.LENGTH_LONG).show();
									}

								}).start();
								 */
								break;

								case(8):
									/*
								new Thread(new Runnable() {
									  @Override
									  public void run()
									  {
										  Toast.makeText(drHandle.getApplicationContext(), "Dota 2 Exited." , Toast.LENGTH_LONG).show();
									  }
								}).start();
									 */

									break;

								default:
									Log.d("CTHREAD", "Defaultt.");

									break;
								}
							}
							Log.d("CTHREAD", "Thread Null.");
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}
			}).start();
		}


	/**
	 * Send data over connection
	 * @param msg String to send
	 */
	static void sendMessage(String msg){
		
		if(out!=null){
			pOut.println(msg);
			//out.writeBytes(msg+"\n");
			// debug
			Log.d("NETIO", "Sending Message: "+msg);

		}
	}
}
/*
// Not using this bish
class NetworkTask extends AsyncTask<String,Void, Void> {
    Callback callback;
	Socket socket;
	DataInputStream in;
	static DataOutputStream out;
	String serverAddy;
	String action;
	int PORT = 8095;
	String defaultAddr = "192.168.1.26";
	Handler drHandle;
	TextView tvConnectStatus;
	BufferedReader buffR;
	protected Void onPreExecute(String ip){
		serverAddy=ip;

		in = null;
		out = null;
		return null;
	}

    protected Void doInBackground(Void... params) {

    	System.out.println("Client Thread Running...");
    	Log.d("CTHREAD", "Client Thread Running...");
    	try {
    		// Socket Details
    		socket = new Socket();
    		SocketAddress adr = new InetSocketAddress(serverAddy, PORT);
    		socket.connect(adr, 1500);
    		// Update connection status on UI

    		// Create IO streams for communication
    		out = new DataOutputStream(socket.getOutputStream());
    		in = new DataInputStream(socket.getInputStream());
    		out.flush();
    		buffR = new BufferedReader(new InputStreamReader(in));
    	} catch (SocketTimeoutException e) {
    		System.err.println(" Error Connecting: \n" + e);
    	} catch (UnknownHostException e) {		
    		System.err.println(" UnknownHostExceptiont \n" + e);
    		System.exit(1);
    	} catch (IOException e) {
    		e.printStackTrace();
    	}catch (Exception e){
    		e.printStackTrace();
    	}

    	Log.d("CTHREAD", "Connect attempted");
    	new Thread(new Runnable(){
    		@Override
    		public void run() {
    			int action = 0;
    			String buff = "";

    			while(!buffR.equals(null)){
    				try {
    					Log.d("CTHREAD", "Listening");
    					buff = buffR.readLine();

    					if(buff!=null && !buff.equals("")){
    						try{
    							action = Integer.parseInt(buff.substring(0,2));
    						}catch(NumberFormatException nfe){
    							nfe.printStackTrace();
    							action = 0;
    						}
    						Log.d("CTHRE", buff);
    						switch(action){
    						case(1):

    							/*new Thread(new Runnable() {
								@Override
								public void run()
								{
									Log.d("CTHREAD", "Dota 2 Started.");
								    Toast.makeText(drHandle.getApplicationContext(), "Dota 2 Started." , Toast.LENGTH_LONG).show();
								}

							}).start();

    							break;

    						case(8):
    							/*
							new Thread(new Runnable() {
								  @Override
								  public void run()
								  {
									  Toast.makeText(drHandle.getApplicationContext(), "Dota 2 Exited." , Toast.LENGTH_LONG).show();
								  }
							}).start();


    							break;

    						default:
    							Log.d("CTHREAD", "Defaultt.");

    							break;
    						}
    					}
    					Log.d("CTHREAD", "Thread Null.");
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
    			}

    		}
    	}).start();

    	return null;
    }

    protected Void onPostExecute(Void... params) {
    	return null;
    }

	@Override
	protected Void doInBackground(String... params) {
		return null;
	}
	static void sendMessage(String msg){
		if(out!=null){
			try {
				out.writeBytes(msg+"\n");
				out.flush();
				// debug
				Log.d("NETIO", "Sending Message: "+msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
 */
/*
/**
 * API 11 only... so maybe later implementation
 * @author User
 *

class ConfirmExitDialogFragment extends DialogFragment{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState){
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.exit_dota)
		.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Close doters
			}
		})
		.setNegativeButton("No", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Cancel
			}
		});
		return builder.create();
	}
}*/