package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    String crashed_port = null;
    boolean crash_status = false;
    static final int SERVER_PORT = 10000;
    String type_of_msg[]={"New","Proposed","Agreed"};
    int msg_counter=0;
    int default_sequence_num=Integer.MIN_VALUE;
    String msg_id=null;
    float seen_priority=0;
    String myPort=null;


    Map<String, List<Float>> proposal=new HashMap<String, List<Float>>();
    HashMap<String,String> msg_id_seq=new HashMap<String, String>();
    PriorityBlockingQueue<String> final_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        //Initializing maximum seen priority with device id decimal to avoid conflicts in ordering
        if(myPort.equals("11108"))
            seen_priority=(float)0.1;
        else if(myPort.equals("11112"))
            seen_priority=(float)0.2;
        else if(myPort.equals("11116"))
            seen_priority=(float)0.3;
        else if(myPort.equals("11120"))
            seen_priority=(float)0.4;
        else if(myPort.equals("11124"))
            seen_priority=(float)0.5;

        //Custom comparator for priority queue to sort messages by their priority
        final Comparator<String> comparator = new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                Float a = Float.parseFloat(lhs.substring(0,lhs.indexOf("#")));
                Float b = Float.parseFloat(rhs.substring(0,rhs.indexOf("#")));
                return a.compareTo(b);
            }
        };
        //Initializing the priority queue with initial capacity = 1 and custom comparator
        final_list = new PriorityBlockingQueue<String>(1,comparator);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }


        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        final EditText editText = (EditText) findViewById(R.id.editText1);

        /* from android official documents */

        final Button button = (Button) findViewById(R.id.button4);

        // registering and implementing the OnClickListener

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                msg_id=myPort+"_"+String.valueOf(msg_counter);
                msg_counter++;

                // Code taken from PA1

                String msg = editText.getText().toString();
                editText.setText(""); // This is one way to reset the input box.
                TextView Text_1 = (TextView) findViewById(R.id.textView1);
                //Text_1.append("\t" + msg);

                String newMsg=default_sequence_num+"#"+msg_id+"#"+msg+"#"+myPort+"#"+type_of_msg[0]+"#"+"0";

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newMsg, myPort);

            }
        });
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String messages = null;
            int msg_seq = 0;
            String msg_received[];


            try {
                while (true) {
                    Socket socket = serverSocket.accept();

                    InputStream in = socket.getInputStream();
                    DataInputStream data = new DataInputStream(in);
                    messages = data.readUTF();


                    OutputStream out = socket.getOutputStream();
                    DataOutputStream dout = new DataOutputStream(out);

                    dout.writeUTF("OK");

                   msg_received=messages.split("#");

                    if(msg_received[4].equals(type_of_msg[0]))
                    {
                        //New message received. Must propose a priority for that message
                        seen_priority=seen_priority+1;
                        msg_received[4]=type_of_msg[1];
                        msg_received[0]= String.valueOf(seen_priority);
                        msg_received[5]=myPort;
                        String msg_after_proposal=msg_received[0]+"#"+msg_received[1]+"#"+msg_received[2]+"#"+msg_received[3]+
                                "#"+msg_received[4]+"#"+msg_received[5];

                        msg_id_seq.put(msg_received[1],msg_after_proposal);
                        final_list.add(msg_after_proposal);

                        publishProgress(msg_after_proposal);


                    }
                    else if(msg_received[4].equals(type_of_msg[1]))
                    {
                        //Proposed order received from an AVD for a message sent by this AVD
                        Log.d(TAG,msg_received[4]);

                       if(proposal.containsKey(msg_received[1]))

                       {
                           proposal.get(msg_received[1]).add(Float.parseFloat(msg_received[0]));
                           if((proposal.get(msg_received[1]).size()==5) || (crash_status == true && proposal.get(msg_received[1]).size() == 4)) {
                               float max_priority = 0;
                               for (int i = 0; i < proposal.get(msg_received[1]).size(); i++) {
                                   if (proposal.get(msg_received[1]).get(i) > max_priority)
                                       max_priority = proposal.get(msg_received[1]).get(i);
                               }

                               msg_received[0] = String.valueOf(max_priority);

                               msg_received[4] = type_of_msg[2];
                               msg_received[5] = myPort;
                               String msg_after_max_priority = msg_received[0] + "#" + msg_received[1] + "#" + msg_received[2] + "#" + msg_received[3] +
                                       "#" + msg_received[4] + "#" + msg_received[5];
                               publishProgress(msg_after_max_priority);
                           }
                       }
                        else
                       {
                           List<Float> list = new ArrayList<Float>();
                           list.add(Float.parseFloat(msg_received[0]));
                           proposal.put(msg_received[1],list);
                       }



                        }
                    else if(msg_received[4].equals(type_of_msg[2]))

                    {
                        //Finaly agreed priority received for a message. Must add to priority queue and deliver if possible.
                        Log.d(TAG,"Agreed");

                        final_list.remove(msg_id_seq.get(msg_received[1])); // removing that id details
                        final_list.add(messages); //adding the agreed detail

                       while(!final_list.isEmpty())
                        {
                            String x=final_list.poll();
                            String temp[]=x.split("#");
                            if(temp[4].equals(type_of_msg[2]))
                            {
                                Uri build = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");

                                // Snippet as  given in the instructions in PA2 description

                                ContentValues keyValueToInsert = new ContentValues();
                                keyValueToInsert.put("key", msg_seq++);
                                keyValueToInsert.put("value", temp[2]);
                                Uri newUri = getContentResolver().insert(
                                        build,
                                        keyValueToInsert
                                );
                            }else if(temp[3].equals(crashed_port)){
                                continue;
                            }
                            else
                            {
                                final_list.add(x);
                                break;
                            }
                        }


                    }
                   // publishProgress(messages);


                    in.close();
                    data.close();
                    out.close();
                    dout.close();
                    socket.close();
                }

            } catch (Exception e) {
                Log.e(TAG, "Server error" + e);
            }
             /*

             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            return null;
        }


        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
            */

            // Log.v(TAG, "Check statement");
            String msg[]=strings[0].split("#");

            if(msg[4].equals(type_of_msg[2]))
            {
                //Priority accepted by this AVD and must broadcast to every other AVD.

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strings[0]);
                /*String strReceived = strings[0].trim();
                TextView Text_1 = (TextView) findViewById(R.id.textView1);
                Text_1.append(strReceived + "\t\n");*/


            }
            else if (msg[4].equals(type_of_msg[1]))
            {
                //Proposing a priority for a received message
                new Proposal_ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, strings[0]);
            }



        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {


            String remotePort[] = new String[5];
            remotePort[0] = REMOTE_PORT0;
            remotePort[1] = REMOTE_PORT1;
            remotePort[2] = REMOTE_PORT2;
            remotePort[3] = REMOTE_PORT3;
            remotePort[4] = REMOTE_PORT4;




                for (int i = 0; i < remotePort.length; i++)
                {
                    try {
                        if(remotePort[i].equals(crashed_port)){
                            continue;
                        }
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort[i]));


                    String msgToSend = msgs[0];
                    Log.v(TAG, "ClientSocket" + remotePort[i]);

                    OutputStream out = socket.getOutputStream();
                    DataOutputStream d = new DataOutputStream(out);
                    d.writeUTF(msgToSend);

                    String ack;

                    InputStream in = socket.getInputStream();
                    DataInputStream din = new DataInputStream(in);

                    ack = din.readUTF();

                    if (ack.equals("OK"))
                        socket.close();

                    out.close();
                    d.close();
                    in.close();
                    din.close();

                }catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Some AVD Failed!");
                    crash_status = true;
                    crashed_port = remotePort[i];
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }

            return null;
        }

    }

    private class Proposal_ClientTask extends AsyncTask<String, Void, Void>
    {
        protected Void doInBackground(String... msgs) {

            String msg_with_proposed_priority[]=msgs[0].split("#");

            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msg_with_proposed_priority[3]));


                OutputStream out = socket.getOutputStream();
                DataOutputStream d = new DataOutputStream(out);
                d.writeUTF(msgs[0]);

                String ack;

                InputStream in = socket.getInputStream();
                DataInputStream din = new DataInputStream(in);

                ack = din.readUTF();

                if (ack.equals("OK"))
                    socket.close();

                out.close();
                d.close();
                in.close();
                din.close();
            }
            catch (UnknownHostException e)
            {
                Log.e(TAG,"Unknown");
            }
            catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }

    }

}
