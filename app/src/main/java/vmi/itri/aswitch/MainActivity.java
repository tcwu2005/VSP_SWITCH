package vmi.itri.aswitch;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
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

import static android.R.attr.defaultValue;
import static android.R.attr.prompt;
import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    static String TAG="SWITCH";
    static String VERSION="v0.91" ;

    TextView text;
    int count = 0;
    String cmd=null;
    TelnetClient tc = null;
    boolean isHost=true;            /* isHost== true, isGuestOn=true_or_false */
    boolean isGuestOn=false;        /* isHost==false, isGuestOn dontcare */
    boolean isPollingStatus=false;

    /* inter-thread exchange */
    String strExchangeMsg = "";
    private final int step1 = 1, step2 = 2, step3 = 3, finish = 4;

    Button btn1, btn2, btn3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /*        buttons      */
        btn1 = (Button) findViewById(R.id.button1);
        btn2 = (Button) findViewById(R.id.button2);
        btn3 = (Button) findViewById(R.id.button3);
        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);
        btn3.setOnClickListener(this);


        /*        TextView        */
        text = (TextView)findViewById(R.id.textView);
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setText("工程版" +VERSION+"\n");


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
            //// TODO: 3/8/17 remove this after bottons all done
            //btn1.setAlpha(.5f);
            //btn3.setAlpha(.5f);
            //btn1.setClickable(false);
            //btn3.setClickable(false);
        }

        /*  If its host , check if Guest is on */
        if(isHost) {
            doCheckAndSetContainerFlag();
        }else {
            isGuestOn = false;
        }

        while(isPollingStatus==true);
        doSetButtons();

    }

    void doSetButtons(){
        Log.e(TAG,"doSetButtons: isHost="+isHost+",isGuestOn="+isGuestOn);
        if(isHost&&!isGuestOn){ //Single host only
            //2,3 off
            btn2.setAlpha(.5f);
            btn3.setAlpha(.5f);
            btn2.setClickable(false);
            btn3.setClickable(false);
            // 1 on
            btn1.setAlpha(1f);
            btn1.setClickable(true);
        }else if (isHost&&isGuestOn){   //Host + VM
            //2,3 on
            btn2.setAlpha(1f);
            btn3.setAlpha(1f);
            btn2.setClickable(true);
            btn3.setClickable(true);
            // 1 off
            btn1.setAlpha(.5f);
            btn1.setClickable(false);
        }else{  //in VM
            //1,3 off
            btn1.setAlpha(.5f);
            btn3.setAlpha(.5f);
            btn1.setClickable(false);
            btn3.setClickable(false);
            //2 on
            btn2.setAlpha(1f);
            btn2.setClickable(true);
        }
    }
    void doHaltAllButtons(){
        btn1.setAlpha(.5f);
        btn2.setAlpha(.5f);
        btn3.setAlpha(.5f);
        btn1.setClickable(false);
        btn2.setClickable(false);
        btn3.setClickable(false);
        try {
            sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {


        switch (v.getId()) {
            case R.id.button1:
                Log.e(TAG,"button1");
                VM_START();
                isGuestOn=true;
                break;

            case R.id.button2:
                Log.e(TAG,"button2");
                if (isGuestOn || !isHost) {
                    VM_SWITCH_HOST_nVM();
                }else{
                    text.append("N/A\n");
                }
                break;

            case R.id.button3:
                Log.e(TAG,"button3");
                if(isGuestOn) {
                    doTerminateGuest();
                    isGuestOn=false;
                }else{
                    text.append("start guest vm first\n");
                }
                break;

            default:
                break;
        }

        /* I might need to do some sneaky things when everytime buttons is pressed */
        doCheckAndSetContainerFlag();
        while(isPollingStatus==true);
        doSetButtons();
        /* End of sneaky */

    }
    /*  Used in both Host and VM   */
    private void VM_SWITCH_HOST_nVM(){
        Runnable r = new Runnable() {
            @Override
            public void run() {
                InputStream in;
                PrintStream out;
                StringBuffer cb;
                StackTraceElement l = new Exception().getStackTrace()[0];
                TelnetClient tc = new TelnetClient();
                try {
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    // Get input and output stream references
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());
                    if(isHost) {
                        Log.e(TAG, "do vm switch...");

                        //modify here
                        cb=readUntilPrompt(in);
                        doPushStringToUI(cb.toString());

                        out.println("/data/maru/host-switch.sh");
                        out.flush();

                        cb=readUntilPrompt(in);
                        doPushStringToUI(cb.toString());

                        sleep(120*1000);   //120s
                        tc.disconnect();
                        doPushStringToUI( l.getClassName()+"/"+l.getMethodName()+":"+l.getLineNumber()+"info:normal disconnect due to timeout"  );
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
    private StringBuffer readUntilPrompt(InputStream in){
        int ch;
        StringBuffer sb=new StringBuffer();
        String pattern = "#";     //prompt pattern

        //String str="";

        try {
            //read till eof or match pattern
            while ( (ch=in.read()) >= 0 ){
                    //Log.i(TAG,"|"+(char)ch+"|"+ch+"|");

                sb.append((char)ch);
                //str = str + (char)ch;

                if ( sb.toString().endsWith(pattern))
                    break;
            }
            Log.d(TAG,">"+sb.toString());
                    //Log.e(TAG,"str="+str);
            return sb;
        } catch (Exception e) {
            e.printStackTrace();
        }

        //return empty string for compipler's requirement
        return sb;
    }

    /*  Read Until End-of-stream */
    /* Maybe this is useless since we can use while(true) readUntilPrompt; */
    /*
    private StringBuffer readUntilEos(InputStream in){
        int ch;
        StringBuffer sb=new StringBuffer();
        try {
            //read till eof
            while ( (ch=in.read()) >= 0 ){
                sb.append((char)ch);
            }
            return sb;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb;
    }
    */
    //// TODO: 3/8/17 Embedded Msg string from telnet thread to textview in object to avoid race condition 
    private void doPushStringToUI(String str){
        strExchangeMsg = str;
        Message msg = new Message();
        msg.what=step1;
        uiMessageHandler.sendMessage(msg);
        try {
            sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    Handler uiMessageHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            //讀出ui.xml中的描述用TextView
            TextView tv =
                    (TextView)findViewById(R.id.textView);

            switch (msg.what){
                case step1:     //telnetClient thread -> UI thread
                    //tv.setText(R.string.processing_step1);
                    Log.e(TAG,"step1 ");
                    tv.append(strExchangeMsg);
                    //strExchangeMsg="";        //this one block other messsages.... (but why?)
                    break;
                case step2:
                    //tv.setText(R.string.processing_step2);
                    break;
                case step3:
                    //tv.setText(R.string.processing_step3);
                    break;
                case finish:
                    //tv.setText(R.string.finish);
                    //pBar.setVisibility(View.INVISIBLE);
                    //pImg.setVisibility(View.VISIBLE);
                    //tv.setText("完成。");
                    //thread.interrupt();
                    break;
                default:
                    break;
            }
        }
    };

    private void doCheckAndSetContainerFlag(){
        Runnable r = new Runnable() {
            @Override
            public void run() {
                isPollingStatus=true;
                InputStream in;
                PrintStream out;
                StringBuffer cb;
                try {
                    TelnetClient tc = new TelnetClient();
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    // Get input and output stream references
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());
                    if(isHost) {
                        cb=readUntilPrompt(in);
                        doPushStringToUI(cb.toString());

                        out.println("cd /data/maru/con1;./vm-st.sh");
                        out.flush();

                        cb = readUntilPrompt(in);
                        doPushStringToUI(cb.toString());

                        if( cb.toString().contains("ON")){
                            isGuestOn=true;
                        }else{
                            isGuestOn=false;
                        }

                        doPushStringToUI("|isGuestOn="+isGuestOn+"|\n");

                        tc.disconnect();
                    }else{
                        String err = "doCheckAndSetContainerFlag should not called in guest";
                        Log.e(TAG, err);
                        doPushStringToUI(err);
                        tc.disconnect();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isPollingStatus=false;
            }
        };
        isPollingStatus=true;   //need bo be at the first line!!!
        /*  print current method name */
        Log.e(TAG,Thread.currentThread().getStackTrace()[2].getMethodName());
        new Thread(r).start();
    }
    private void doTerminateGuest(){
        Runnable r = new Runnable() {
            @Override
            public void run() {
                InputStream in;
                PrintStream out;
                StringBuffer cb;
                try {
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    // Get input and output stream references
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());
                    if(isHost) {
                        Log.e(TAG, "ready to terminate guest...");

                        //modify here
                        cb=readUntilPrompt(in);
                        doPushStringToUI(cb.toString());

                        out.println("cd /data/maru/con1;./vm-stop.sh");
                        out.flush();

                        sleep(100); //100ms

                        while (true) {  //blocking endlessly
                            cb = readUntilPrompt(in);
                            doPushStringToUI(cb.toString());
                        }
                        //readUntilEos(in);
                        //doPushStringToUI(cb.toString());
                    }else{
                        Log.e(TAG, ">>>>It is impossible that a guest quit itself..");
                        doPushStringToUI(">>>>It is impossible that a guest quit itself..");
                        //tc.disconnect();
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
    void VM_START(){
        Runnable mutiThread = new Runnable() {          /* telnet thread */
            InputStream in;
            PrintStream out;
            StringBuffer cb;
            int ch;
            String str;
            StackTraceElement l = new Exception().getStackTrace()[0];
            TelnetClient tc;
            @Override
            public void run() {
                try {
                    tc = new TelnetClient();
                    Log.e(TAG,"connectiong to localhost...");
                    tc.connect("localhost");
                    in = tc.getInputStream();
                    out = new PrintStream(tc.getOutputStream());

                    cb=readUntilPrompt(in);
                    doPushStringToUI(cb.toString());

                    Log.e(TAG,"start vm...");
                    out.println("cd /data/maru/con1;./vm-start.sh");
                    out.flush();

                    cb=readUntilPrompt(in);
                    doPushStringToUI(cb.toString());

                    sleep(120*1000);   //120s
                    tc.disconnect();
                    doPushStringToUI( l.getClassName()+"/"+l.getMethodName()+":"+l.getLineNumber()+"info:normal disconnect due to timeout"  );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Log.e(TAG,"button1 clicked...");
        //cmd="pwd\n";
        Thread thread = new Thread(mutiThread);
        thread.start();
        isGuestOn=true;
    }
}
