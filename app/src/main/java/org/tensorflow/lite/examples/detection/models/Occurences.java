package org.tensorflow.lite.examples.detection.models;

public class Occurences {

    private String objectTitle;
    private String objectOccurence;
    public int occ;

    public Occurences(String objectTitle) {
        this.objectTitle = objectTitle;
    }


    public String getObjectTitle() {
        return this.objectTitle;
    }

    public String getObjectOccurence() {
        return this.objectOccurence;
    }

    public void setObjectTitle(String title) {
        this.objectTitle = title;
    }

    public void setObjectOccurence(String occurence) {
        this.objectOccurence = occurence;
    }
}
