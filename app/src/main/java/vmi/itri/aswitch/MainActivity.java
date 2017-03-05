package vmi.itri.aswitch;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.StringTokenizer;

import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import org.apache.commons.net.telnet.SimpleOptionHandler;
import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import java.io.File;

import static android.R.attr.prompt;
import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    static String TAG="SWITCH";
    TextView text;
    int count = 0;
    String cmd=null;
    TelnetClient tc = null;
    boolean isHost=true;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /*        buttons      */
        Button btn1 = (Button) findViewById(R.id.button1);
        Button btn2 = (Button) findViewById(R.id.button2);
        Button btn3 = (Button) findViewById(R.id.button3);
        btn2.setOnClickListener(this);
        btn3.setOnClickListener(this);


        /*        TextView        */
        text = (TextView)findViewById(R.id.textView);
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setText("工程版\n");


        /*        Telnet Client         */
        tc = new TelnetClient();
        TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
        EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
        SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);

        try
        {
            tc.addOptionHandler(ttopt);
            tc.addOptionHandler(echoopt);
            tc.addOptionHandler(gaopt);
        }
        catch (Exception e)
        {
            System.err.println("Error registering option handlers: " + e.getMessage());
        }


        /*   check if it's host or in container    */
        File f = new File("/data/maru/con1");
        if(f.isDirectory())   {
            isHost=true;
            text.append("app in host" );
        }else {
            isHost=false;
            text.append("app in container");
        }


        /*    Button click-handler      */
        btn1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            /* telnet thread */
            Runnable mutiThread = new Runnable() {
                InputStream in;
                PrintStream out;
                int ch;
                String str;
                @Override
                public void run() {
                    try {
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    // Get input and output stream references
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());
                        Log.e(TAG,"start vm...");
                    out.println("cd /data/maru/con1;./vm-start.sh");
                    out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            Log.e(TAG,"button1 clicked...");
            //cmd="pwd\n";
            Thread thread = new Thread(mutiThread);
            thread.start();
            }
        });

        //btn3.setOnClickListener(new OnClickListener(){


        //});
    }



    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.button1:
                Log.e(TAG,"button1");
                break;

            case R.id.button2:
                Log.e(TAG,"button2");
                if (isHost){
                    VM_SWITCH_HOST();
                }else
                    VM_SWITCH_HOST();
                break;

            case R.id.button3:
                Log.e(TAG,"button3");
                break;

            default:
                break;
        }
    }
    private void VM_SWITCH_HOST(){
        Runnable r = new Runnable() {
            @Override
            public void run() {
                InputStream in;
                PrintStream out;
                try {
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    // Get input and output stream references
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());
                    if(isHost) {
                        Log.e(TAG, "do vm switch...");
                        out.println("cd /data/maru/con1;./vm-swch.sh");
                        out.flush();
                        sleep(5000);
                    }else{
                        Log.e(TAG, ">>>>this is a guest vm...");
                        out.println("cd /data/guest;./con1-switch.sh");
                        out.flush();
                        sleep(9000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        /*  print current method name */
        Log.e(TAG,Thread.currentThread().getStackTrace()[2].getMethodName());

        new Thread(r).start();
    }
}
