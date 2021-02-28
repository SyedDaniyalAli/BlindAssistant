package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class TextToSpeechClass{

    TextToSpeech t1;

    TextToSpeechClass(Context context, String textToSpeak)
    {
        t1=new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.UK);
                }
            }
        });

        t1.stop();
        t1.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        t1.stop();
        t1.shutdown();

    }
}
