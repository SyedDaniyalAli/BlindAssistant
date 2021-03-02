package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;


public class TextToSpeechClass {

    TextToSpeech tts;
    Context context;

    TextToSpeechClass(Context context) {
        this.context=context;
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {

            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("error", "This Language is not supported");
                    }
                } else
                    Log.e("error", "Initilization Failed!");
            }
        });

    }

    void startPlayer(String textToSpeak) {
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        tts.stop();
    }

    void stopPlayer() {
        tts.stop();
        tts.shutdown();
    }
}
