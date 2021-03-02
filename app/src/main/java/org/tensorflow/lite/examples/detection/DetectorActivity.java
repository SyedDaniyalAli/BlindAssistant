/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.models.Occurences;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();



    /**
     * This is cyclic speech turn to give the application the ability to speak without being interrupted
     * by new recognized objects.
     * The application only "speaks" the classes recognized after a limit of passed turns without speaking
     */
    private int currentSpeechTurn = 1;
    private static final int TALK_SPEECH_TURN = 0;
    private int limitWithoutTalk = ONE_OBJECT_TURN_LIMIT;
    private static final int ONE_OBJECT_TURN_LIMIT = 2;
    private static final int TWO_OBJECT_TURN_LIMIT = 4;
    private static final int THREE_OBJECT_TURN_LIMIT = 5;
    private static final int FOUR_OBJECT_TURN_LIMIT = 6;
    private static final int FIVE_OBJECT_TURN_LIMIT = 7;
    private static final int HIGHER_OBJECT_TURN_LIMIT = 10;

    private byte[] luminanceCopy;


    //TextToSpeech  Engine initialized
    private TextToSpeech tts;
    static String previousResult = "";


    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Detector detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;




    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        //       Initializing Text to Speech~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        tts = new TextToSpeech(DetectorActivity.this, new TextToSpeech.OnInitListener() {

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
//        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }


        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Detector.Recognition> mappedRecognitions =
                                new ArrayList<Detector.Recognition>();


                        for (final Detector.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

////                                Text to Speak~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//                                if (!previousResult.contains("" + result.getTitle())) {
//                                    tts.speak("There is a :" + result.getTitle(), TextToSpeech.QUEUE_ADD, null);
//                                    previousResult = result.getTitle();
//                                }

//                                tts.stop();
//                                Toast.makeText(DetectorActivity.this, "" + result.getTitle(), Toast.LENGTH_SHORT).show();

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        //Here we are trying to not let tracked boxes follow the object when it isn't being recognized anymore
                        // TODO(Ihab): there is a much better method to use the tracker, Once there is time I should
                        // the recognitions of the tracker get SAFELY (I stress the word) into mappedRecognitions to be reused later
                        if (currentSpeechTurn==TALK_SPEECH_TURN){
                            tracker.trackResults(mappedRecognitions, currTimestamp);
                        } else if (currentSpeechTurn==limitWithoutTalk){
                            tracker = new MultiBoxTracker(DetectorActivity.this);
                        }

                        trackingOverlay.postInvalidate();

//                         It's not working
//                        requestRender();


                        /**
                         * This is the thread where the recognized results get processed to know whether they
                         * should be "spoken" or not and how and when
                         */
                        Thread logoTimer = new Thread() {
                            public void run() {
                                try {
                                    //These conditions are in order to know when to speak, whenever there are results
                                    // and the its the turn of speaking.
                                    if (mappedRecognitions.size()==0 && currentSpeechTurn == TALK_SPEECH_TURN){
                                        // In this case, there is no object recognized, so we stay in the turn of speaking
                                        // until we find an object to recognize.
                                        currentSpeechTurn = TALK_SPEECH_TURN;
                                    }else if (mappedRecognitions.size()>0 && currentSpeechTurn == TALK_SPEECH_TURN){

                                        /**
                                         * here we create an array list of the Occurrences of each object, using the
                                         * mappedRecognitions while avoiding repetition. This is in order to give concise speech as output
                                         * with processed results and copy normal speech while using all grammatical rules as much as possible
                                         * Yep! Abstraction is important even in speech! :D
                                         */
                                        List<Occurences> mappedOccurrences = new ArrayList<>();
                                        for (Detector.Recognition mapped : mappedRecognitions){

                                            int occu = 0;
                                            Occurences tempOccurrence = new Occurences(mapped.getTitle());

                                            for (Detector.Recognition mapping : mappedRecognitions){
                                                if (mapped.getTitle().equals(mapping.getTitle())){
                                                    occu++;
                                                }
                                            }

                                            tempOccurrence.setObjectOccurence(occu + "");
                                            tempOccurrence.occ = occu;
                                            //

                                            boolean add = true;
                                            if (mappedOccurrences.size()>0){
                                                for ( Occurences mapOccu : mappedOccurrences){
                                                    if (!tempOccurrence.getObjectTitle().equals(mapOccu.getObjectTitle())){
                                                        add = true;
                                                    }else{
                                                        add = false;
                                                        break;
                                                    }
                                                }
                                                if(add){
                                                    mappedOccurrences.add(tempOccurrence);
                                                }

                                            }else{
                                                mappedOccurrences.add(tempOccurrence);
                                            }
                                        }


                                        /**
                                         * This is a crude way to dynamically change the duration of wait for each speech,
                                         * depending on how long it is each time, to make it very comfortable with no wait time.
                                         */
                                        // TODO (Ihab) : I gotta make this little part into a method to be reused later
                                        // in future Models.
                                        if(mappedOccurrences.size()==1){
                                            limitWithoutTalk = ONE_OBJECT_TURN_LIMIT;
                                        } else if (mappedOccurrences.size()==2){
                                            limitWithoutTalk = TWO_OBJECT_TURN_LIMIT;
                                        } else if (mappedOccurrences.size()==3){
                                            limitWithoutTalk = THREE_OBJECT_TURN_LIMIT;
                                        } else if (mappedOccurrences.size()== 4){
                                            limitWithoutTalk = FOUR_OBJECT_TURN_LIMIT;
                                        } else if (mappedOccurrences.size()==5){
                                            limitWithoutTalk = FIVE_OBJECT_TURN_LIMIT;
                                        }else{
                                            limitWithoutTalk = HIGHER_OBJECT_TURN_LIMIT;
                                        }
                                        sleep(500);
                                        //This firstSpeak will flush (interrupt) the older speech. They already had ample time to finish their speech
                                        // and we don't need a long Queue of words waiting their turn. It has to be as "real-time" as possible.
                                        firstSpeak("");
                                        int occurenceIndex = 0;
                                        for (Occurences spokenOccurence : mappedOccurrences){
                                            String plural_s;

                                            /**
                                             * This part makes the recognitions into plural if there are many occurences of it
                                             * The words feel cringeworthy to a grammatical nazi like me if they aren't perfect
                                             * the irregular words are changed first into their plural, and then an "s" is added to regular words.
                                             * The world should be changed if the labels are changed.
                                             */
                                            // TODO (Ihab) : Make this too a method, so that its reused in other Neural Networks
                                            if (spokenOccurence.occ >1){
                                                plural_s = "";
                                                if (spokenOccurence.getObjectTitle().equals("person")){
                                                    spokenOccurence.setObjectTitle("people");
                                                } else if (spokenOccurence.getObjectTitle().equals("bus")){
                                                    spokenOccurence.setObjectTitle("buses");
                                                } else if (spokenOccurence.getObjectTitle().equals("bench")){
                                                    spokenOccurence.setObjectTitle("benches");
                                                } else if (spokenOccurence.getObjectTitle().equals("skis")){
                                                    spokenOccurence.setObjectTitle("skis");
                                                } else if (spokenOccurence.getObjectTitle().equals("wine glass")){
                                                    spokenOccurence.setObjectTitle("wine glasses");
                                                } else if (spokenOccurence.getObjectTitle().equals("sandwich")){
                                                    spokenOccurence.setObjectTitle("sandwiches");
                                                } else if (spokenOccurence.getObjectTitle().equals("couch")){
                                                    spokenOccurence.setObjectTitle("couches");
                                                } else if (spokenOccurence.getObjectTitle().equals("scissors")){
                                                    spokenOccurence.setObjectTitle("scissors");
                                                } else if (spokenOccurence.getObjectTitle().equals("piece of bread")){
                                                    spokenOccurence.setObjectTitle("pieces of bread");
                                                } else {
                                                    plural_s = "s";
                                                }
                                            }else{
                                                plural_s = "";
                                            }

                                            /**
                                             * Gramma Nazi mode again :D
                                             * "And" should be added before the last recognition to be spoken. That way,
                                             * it gives a smooth speech, and would signal the end of the speech.
                                             */
                                            String and;
                                            if (mappedOccurrences.size()==1){
                                                and = "";
                                            } else if(occurenceIndex == mappedOccurrences.size()-1){
                                                and = "and ";
                                            } else {
                                                and = "";
                                            }
                                            occurenceIndex++;
                                            speakOut(and + spokenOccurence.getObjectOccurence()+ " " +spokenOccurence.getObjectTitle() + plural_s);
                                        }
                                        currentSpeechTurn++;
                                    }else if(currentSpeechTurn==limitWithoutTalk){
                                        //When you reach the end of cyclical turn, u go back to the start, which is the turn of the speech
                                        currentSpeechTurn = TALK_SPEECH_TURN;
                                    }else{
                                        currentSpeechTurn++;
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                            }
                        };
                        logoTimer.start();


                        // Stopping Default tracker on screen~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//                        tracker.trackResults(mappedRecognitions, currTimestamp);
//                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(
                () -> {
                    try {
                        detector.setUseNNAPI(isChecked);
                    } catch (UnsupportedOperationException e) {
                        LOGGER.e(e, "Failed to set \"Use NNAPI\".");
                        runOnUiThread(
                                () -> {
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                });
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    @Override
    public synchronized void onDestroy() {
        tts.speak("I m Shutting down", TextToSpeech.QUEUE_FLUSH, null);
        super.onDestroy();
        tts.stop();
        tts.shutdown();
    }

    private void speakOut(String text) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null);
    }

    private void firstSpeak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
