package com.matbadev.openairplay;

import java.awt.image.BufferedImage;

class PhotoThread extends Thread {

    private final AirPlay airplay;
    private BufferedImage image;
    private int timeout;

    public PhotoThread(AirPlay airplay) {
        this(airplay, null, 1000);
    }

    public PhotoThread(AirPlay airplay, BufferedImage image, int timeout) {
        this.airplay = airplay;
        this.image = image;
        this.timeout = timeout;
    }

    public void run() {
        while (!Thread.interrupted()) {
            try {
                if (image == null) {
                    BufferedImage frame = airplay.scaleImage(AirPlay.captureScreen());
                    airplay.photoRawCompress(frame, Config.NONE);
                } else {
                    airplay.photoRaw(image, Config.NONE);
                    Thread.sleep(Math.round(0.9 * timeout));
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

}
