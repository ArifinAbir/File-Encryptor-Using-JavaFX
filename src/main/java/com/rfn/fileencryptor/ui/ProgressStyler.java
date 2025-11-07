package com.rfn.fileencryptor.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Applies a consistent, animated look and feel to progress bars across the app.
 */
public final class ProgressStyler {

    private static final String DEFAULT_ACCENT = "#0099ff";

    private ProgressStyler() {
    }

    /**
     * Smoothly animates the supplied progress bar towards {@code target} while applying
     * the accent styling.
     *
     * @param bar         target progress bar
     * @param target      desired progress value (0.0 - 1.0)
     * @param accentColor accent color in hex form; falls back to default when blank
     */
    public static void animateProgressBar(ProgressBar bar, double target, String accentColor) {
        if (bar == null) {
            return;
        }
        String accent = (accentColor == null || accentColor.isBlank()) ? DEFAULT_ACCENT : accentColor;
        ensureProfessionalSkin(bar, accent);

        double clamped = Math.max(0.0, Math.min(1.0, target));
        Object existing = bar.getProperties().get("progressAnimation");
        if (existing instanceof Timeline timeline) {
            timeline.stop();
        }
        bar.getProperties().remove("progressAnimation");

        KeyValue keyValue = new KeyValue(bar.progressProperty(), clamped, Interpolator.EASE_BOTH);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(250), keyValue));
        bar.getProperties().put("progressAnimation", timeline);
        timeline.setOnFinished(evt -> bar.getProperties().remove("progressAnimation"));
        timeline.play();
    }

    /**
     * Ensures the progress bar uses the custom accent and animated stripes.
     *
     * @param bar        target progress bar
     * @param accentHex  accent color in hex form
     */
    public static void ensureProfessionalSkin(ProgressBar bar, String accentHex) {
        if (bar == null) {
            return;
        }
        final String effectiveAccent = (accentHex == null || accentHex.isBlank()) ? DEFAULT_ACCENT : accentHex;
        Runnable apply = () -> {
            Color accent = Color.web(effectiveAccent);
            Node trackNode = bar.lookup(".track");
            if (trackNode instanceof StackPane trackRegion) {
                styleTrackRegion(trackRegion, accent);
            }
            Node barNode = bar.lookup(".bar");
            if (barNode instanceof StackPane barRegion) {
                styleBarRegion(barRegion, accent);
                ensureStripedOverlay(barRegion, accent);
            }
        };

        if (bar.getSkin() == null) {
            if (!Boolean.TRUE.equals(bar.getProperties().get("proSkinListener"))) {
                bar.getProperties().put("proSkinListener", Boolean.TRUE);
                bar.skinProperty().addListener((obs, oldSkin, newSkin) -> {
                    if (newSkin != null) {
                        Platform.runLater(apply);
                    }
                });
            }
            bar.applyCss();
        } else {
            Platform.runLater(apply);
        }
    }

    private static void styleTrackRegion(StackPane trackRegion, Color accent) {
        Color border = accent.interpolate(Color.WHITE, 0.45);
        trackRegion.setStyle(String.format(
                "-fx-background-color: rgba(12,18,32,0.82);"
                        + "-fx-background-radius: 20;"
                        + "-fx-background-insets: 0;"
                        + "-fx-border-color: %s;"
                        + "-fx-border-radius: 20;"
                        + "-fx-border-width: 1.25;"
                        + "-fx-effect: innershadow(gaussian, rgba(0,0,0,0.65), 12, 0.55, 0, 2);",
                toRgba(border, 0.55)
        ));
    }

    private static void styleBarRegion(StackPane barRegion, Color accent) {
        Color highlight = accent.interpolate(Color.WHITE, 0.45);
        Color base = accent.interpolate(Color.WHITE, 0.12);
        Color shadow = accent.interpolate(Color.BLACK, 0.28);
        barRegion.setStyle(String.format(
                "-fx-background-color: linear-gradient(to right, %s 0%%, %s 45%%, %s 100%%);"
                        + "-fx-background-radius: 18;"
                        + "-fx-background-insets: 0;"
                        + "-fx-border-color: rgba(255,255,255,0.25);"
                        + "-fx-border-radius: 18;"
                        + "-fx-border-width: 1.0;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 18, 0.45, 0, 4);",
                toRgba(highlight, 0.95),
                toRgba(base, 0.98),
                toRgba(shadow, 1.0)
        ));
    }

    private static void ensureStripedOverlay(StackPane barRegion, Color accent) {
        Region stripes = (Region) barRegion.getProperties().get("stripedRegion");
        if (stripes == null) {
            stripes = new Region();
            stripes.setMouseTransparent(true);
            stripes.setPickOnBounds(false);
            stripes.setManaged(false);
            stripes.setOpacity(0.75);

            Region finalStripes = stripes;
            barRegion.widthProperty().addListener((obs, oldVal, newVal) -> {
                double width = Math.max(1.0, newVal.doubleValue()) * 2.4;
                finalStripes.setPrefWidth(width);
            });
            barRegion.heightProperty().addListener((obs, oldVal, newVal) -> finalStripes.setPrefHeight(newVal.doubleValue()));

            stripes.setPrefWidth(Math.max(1.0, barRegion.getWidth()) * 2.4);
            stripes.setPrefHeight(Math.max(1.0, barRegion.getHeight()));

            barRegion.getChildren().add(stripes);

            Rectangle clip = (Rectangle) barRegion.getProperties().get("barClip");
            if (clip == null) {
                clip = new Rectangle();
                clip.setArcWidth(24);
                clip.setArcHeight(24);
                clip.widthProperty().bind(barRegion.widthProperty());
                clip.heightProperty().bind(barRegion.heightProperty());
                barRegion.setClip(clip);
                barRegion.getProperties().put("barClip", clip);
            }

            TranslateTransition slide = new TranslateTransition(Duration.seconds(1.8), stripes);
            slide.setFromX(0);
            slide.setToX(-80);
            slide.setInterpolator(Interpolator.LINEAR);
            slide.setCycleCount(Animation.INDEFINITE);
            slide.play();

            barRegion.getProperties().put("stripedRegion", stripes);
            barRegion.getProperties().put("stripeAnimation", slide);
        } else {
            TranslateTransition slide = (TranslateTransition) barRegion.getProperties().get("stripeAnimation");
            if (slide != null && slide.getStatus() != Animation.Status.RUNNING) {
                slide.play();
            }
        }

        updateStripeStyle(stripes, accent);
    }

    private static void updateStripeStyle(Region stripes, Color accent) {
        if (stripes == null) {
            return;
        }
        Color bright = accent.interpolate(Color.WHITE, 0.55);
        Color soft = accent.interpolate(Color.WHITE, 0.25);
        Color deep = accent.interpolate(Color.BLACK, 0.25);
        stripes.setStyle(String.format(
                "-fx-background-radius: 18;"
                        + "-fx-background-insets: 0;"
                        + "-fx-background-color: linear-gradient(from 0%% 0%% to 100%% 100%%,"
                        + "%1$s 0%%, %1$s 12%%, %2$s 12%%, %2$s 24%%,"
                        + "%1$s 24%%, %1$s 36%%, %3$s 36%%, %3$s 48%%,"
                        + "%1$s 48%%, %1$s 60%%, %2$s 60%%, %2$s 72%%,"
                        + "%1$s 72%%, %1$s 84%%, %3$s 84%%, %3$s 96%%,"
                        + "%1$s 96%%, %1$s 100%%);"
                        + "-fx-background-repeat: repeat;"
                        + "-fx-background-size: 120 120;"
                        + "-fx-opacity: 0.78;",
                toRgba(bright, 0.9),
                toRgba(soft, 0.55),
                toRgba(deep, 0.65)
        ));
    }

    private static String toRgba(Color color, double opacity) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        double alpha = Math.max(0.0, Math.min(1.0, opacity));
        return String.format("rgba(%d,%d,%d,%.3f)", r, g, b, alpha);
    }
}
