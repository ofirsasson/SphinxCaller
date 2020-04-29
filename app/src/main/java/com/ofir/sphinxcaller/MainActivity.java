package com.ofir.sphinxcaller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

//public class MainActivity extends AppCompatActivity {
public class MainActivity extends Activity implements
        RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String CALL_WORD = "HAYEG";
    private static final String ERASE_WORD = "MEHAK";
    private static final String ZERO_WORD = "APES";
    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static String HypothesisHolder = "";
    private SpeechRecognizer recognizer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.caption_text))
                .setText(R.string.instuctions);
     //   ((TextView) findViewById(R.id.result_text))
    //            .setText(R.string.result_text);
        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        new SetupTask(this).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;

        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                ((TextView) activityReference.get().findViewById(R.id.caption_text))
                        .setText("Failed to init recognizer " + result);
            } else {
                activityReference.get().switchSearch(CALL_WORD);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    //Called when partial recognition result is available. Used also to spot keywords
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;
        if (hypothesis.getHypstr().contains(ZERO_WORD)) {
            HypothesisHolder = "";
            switchSearch(CALL_WORD);
        }
        else
            {
             String text;
             //set text variable with last session hypothesis if exists
             if (HypothesisHolder.equals(""))
                 text = HypothesisHolder + hypothesis.getHypstr();
              else
                  text = HypothesisHolder + " " + hypothesis.getHypstr();
             List<String> words = new ArrayList<String>(Arrays.asList(text.split(" ")));
              boolean ReadyForCall = false;
              while (words.contains(ERASE_WORD)) //erases one char from result_text
              {
                 if (words.size() > 1)
                      words.remove(words.indexOf(ERASE_WORD) - 1);
                 words.remove(words.indexOf(ERASE_WORD));
              }
                while (words.contains(CALL_WORD))
                 if (words.indexOf(CALL_WORD) == words.size() - 1) {
                      ReadyForCall = !ReadyForCall;
                      words.remove(words.indexOf(CALL_WORD));
                 }
                   else
                    words.remove(words.indexOf(CALL_WORD));
               text = DigitConverter(words);
                ((TextView) findViewById(R.id.result_text)).setText(text);
             if (ReadyForCall) {
                 ReadyForCall = !ReadyForCall;
               HypothesisHolder = words.toString().replaceAll("[,\\[\\]]", "");
               recognizer.stop();
               BeforeCall(text);
             }
        }
    }

    // Called after the recognition is ended. code in comments resets result_text and alerting the results
    @Override
    public void onResult(Hypothesis hypothesis) {
       /* ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
        */
    }

    //Called at the beginning of utterance
    @Override
    public void onBeginningOfSpeech() {
        Log.e("beginning","recognized");
    }

    //Called at the end of utterance.
    @Override
    public void onEndOfSpeech() {
        Log.e("ending","recognized");
    }

    //Listen and alert results
    private void switchSearch(String searchName) {
        recognizer.stop();
        recognizer.startListening(searchName);
        ((TextView) findViewById(R.id.result_text)).setText(R.string.result_text);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "1542.dic"))
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .getRecognizer();
        recognizer.addListener(this);
        File languageModel = new File(assetsDir, "1542.lm");
        recognizer.addNgramSearch(CALL_WORD, languageModel);
    }

    // Called after timeout expired
    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    //Called when an error occurs.
    @Override
    public void onTimeout() {
        switchSearch(CALL_WORD);
    }

    //convert hebrew digits to actual digits
    public String DigitConverter (List<String> digit)
    {
        String FinalText = "";
        for (String s : digit)
        switch(s) {
            case "EFES":
                FinalText+="0";
                break ;
            case "EHAD":
                FinalText+="1";
                break ;
            case "SHTAIM":
                FinalText+="2";
                break ;
            case "SHALOSH":
                FinalText+="3";
                break ;
            case "ARBA":
                FinalText+="4";
                break ;
            case "HAMESH":
                FinalText+="5";
                break ;
            case "SHESH":
                FinalText+="6";
                break ;
            case "SHEVA":
                FinalText+="7";
                break ;
            case "SHMONE":
                FinalText+="8";
                break ;
            case "TESHA":
                FinalText+="9";
                break ;
            default:
                break ;
        }
        return FinalText;
    }
    //shows alertdialog message and preforms call if needed
    public void BeforeCall(final String number) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title);
        builder.setMessage("להתקשר אל:" + number + " ?");
        builder.setPositiveButton("אישור", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //check for premission to call or ask for it
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CALL_PHONE}, 1);
                } else {
                    //calling to the number in text and close the app or log the error
                    try {
                         {
                            Intent callIntent = new Intent(Intent.ACTION_CALL);
                            callIntent.setData(Uri.parse("tel:" + number));
                            startActivity(callIntent);
                             HypothesisHolder = "";
                            //closing the app
                            finish();
                            System.exit(0);
                        }
                    } catch (Exception e) {
                        Log.e("error", e.toString());
                    }
                }
            }
        });
        builder.setNegativeButton("בטל", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                recognizer.startListening(CALL_WORD);
                dialog.cancel();
            }
        });
        builder.show();
        }

}
