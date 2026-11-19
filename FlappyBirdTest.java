import java.lang.reflect.*;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;

/** Headless regression checks: java -Djava.awt.headless=true FlappyBirdTest */
public class FlappyBirdTest {
    static Field field(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    static Object get(Object obj, String name) throws Exception { return field(obj, name).get(obj); }
    static void set(Object obj, String name, Object value) throws Exception { field(obj, name).set(obj, value); }
    static void call(Object obj, String name) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(obj);
    }
    static void tick(FlappyBird game, double dt) throws Exception {
        Method m = FlappyBird.class.getDeclaredMethod("updateGame", double.class);
        m.setAccessible(true); m.invoke(game, dt);
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static double value(FlappyBird game, String name) throws Exception {
        Method m = FlappyBird.class.getDeclaredMethod(name);
        m.setAccessible(true); return (double) m.invoke(game);
    }
    static void choose(FlappyBird game, String action) {
        game.getActionMap().get(action).actionPerformed(null);
    }
    static void animate(FlappyBird game, double dt) throws Exception {
        Method m = FlappyBird.class.getDeclaredMethod("animate", double.class);
        m.setAccessible(true); m.invoke(game, dt);
    }
    static int frame(FlappyBird game) throws Exception {
        Method m = FlappyBird.class.getDeclaredMethod("birdFrameIndex");
        m.setAccessible(true); return (int) m.invoke(game);
    }
    static void testWingAnimation(FlappyBird game) throws Exception {
        call(game, "reset"); call(game, "flap");
        check(frame(game) == 0, "Jump starts wings-up");
        animate(game, .06); check(frame(game) == 1, "Jump moves to level wings");
        animate(game, .06); check(frame(game) == 2, "Jump moves to wings-down");
        animate(game, .06); check(frame(game) == 1, "Jump returns through level wings");
        animate(game, .06); check(frame(game) == 0, "Flap cycle loops");
        call(game, "flap");
        check((double) get(game, "wingPhase") == 0, "Repeated jump restarts downstroke");
        set(game, "velocity", 200.0); set(game, "flapPulse", 0.0);
        animate(game, .08); check(frame(game) == 0, "Falling animation is slower");
        animate(game, .08); check(frame(game) == 1, "Falling wings still animate");
        set(game, "wingPhase", 0.0); animate(game, .30);
        double oneStep = (double) get(game, "wingPhase");
        set(game, "wingPhase", 0.0);
        for (int i = 0; i < 30; i++) animate(game, .01);
        check(Math.abs((double) get(game, "wingPhase") - oneStep) < .000001,
                "Wing cadence is independent of update frequency");
        call(game, "gameOver"); int frozen = frame(game);
        animate(game, 1); check(frame(game) == frozen, "Collision freezes wings");
        BufferedImage[] frames = (BufferedImage[]) get(game, "birdFrames");
        BufferedImage preview = new BufferedImage(360, 150, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = preview.createGraphics();
        graphics.setColor(new java.awt.Color(125, 210, 235)); graphics.fillRect(0, 0, 360, 150);
        for (int i = 0; i < frames.length; i++) {
            check(frames[i].getWidth() == 76 && frames[i].getHeight() == 76, "Consistent frame dimensions");
            graphics.drawImage(frames[i], i * 120 + 22, 5, null);
            graphics.drawImage(frames[i], i * 120 + 41, 100, 38, 38, null);
        }
        graphics.dispose();
        new File("build").mkdirs();
        ImageIO.write(preview, "png", new File("build/wing-frames.png"));
        call(game, "reset");
        check(frame(game) == 0, "Reset restores initial wing pose");
    }
    static void testParallax(FlappyBird game) throws Exception {
        call(game, "reset");
        double[] offsets = (double[]) get(game, "backgroundOffsets");
        java.util.Arrays.fill(offsets, 0);
        animate(game, 1);
        check(offsets[0] > 0 && offsets[0] < offsets[1] && offsets[1] < offsets[2],
                "Background layers scroll at distinct depth speeds");
        double normalDistance = offsets[2];
        java.util.Arrays.fill(offsets, 0);
        set(game, "roundTime", 90.0); animate(game, 1);
        check(Math.abs(offsets[2] - normalDistance * 1.4) < .00001, "Parallax follows difficulty ramp");
        double preserved = offsets[2]; call(game, "reset");
        check(offsets[2] == preserved, "Restart preserves scenery position");
        java.util.Arrays.fill(offsets, 1439.99); animate(game, .1);
        for (double offset : offsets) check(offset >= 0 && offset < 10, "Layers wrap without unbounded offsets");
        call(game, "gameOver");
        double[] frozen = offsets.clone(); animate(game, 1);
        check(java.util.Arrays.equals(offsets, frozen), "Parallax freezes on collision");
        call(game, "reset");
        java.util.Arrays.fill(offsets, 0); animate(game, .3);
        double[] singleStep = offsets.clone(); java.util.Arrays.fill(offsets, 0);
        for (int i = 0; i < 30; i++) animate(game, .01);
        for (int i = 0; i < offsets.length; i++)
            check(Math.abs(offsets[i] - singleStep[i]) < .00001, "Parallax independent of update frequency");
        call(game, "flap");
        java.util.Arrays.fill(offsets, 0); preview(game, "parallax-start");
        // All three tile joins are visible in this frame for visual seam checks.
        java.util.Arrays.fill(offsets, 500); preview(game, "parallax-seams");
        call(game, "reset");
    }
    static void click(FlappyBird game, String name) throws Exception {
        ((javax.swing.JButton) get(game, name)).doClick(0);
    }
    static void preview(FlappyBird game, String name) throws Exception {
        game.setSize(480, 640);
        BufferedImage image = new BufferedImage(480, 640, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        game.paint(graphics); graphics.dispose();
        new File("build").mkdirs();
        ImageIO.write(image, "png", new File("build/" + name + ".png"));
    }
    static void testMenu(FlappyBird game) throws Exception {
        check(((javax.swing.JButton) get(game, "playButton")).isVisible(), "Start menu visible");
        preview(game, "menu");
        click(game, "instructionsButton");
        check(get(game, "state").toString().equals("INSTRUCTIONS"), "Instructions button");
        call(game, "flap"); choose(game, "restart"); tick(game, .1);
        check(get(game, "state").toString().equals("INSTRUCTIONS")
                && (double) get(game, "roundTime") == 0, "Instructions do not start gameplay");
        preview(game, "instructions");
        click(game, "backButton");
        check(get(game, "state").toString().equals("READY"), "Back button");
        click(game, "playButton");
        check(get(game, "state").toString().equals("PLAYING")
                && !((javax.swing.JButton) get(game, "playButton")).isVisible(), "Play hides menu and starts round");
        choose(game, "menu");
        check(get(game, "state").toString().equals("READY")
                && ((List<?>) get(game, "pipes")).isEmpty(), "Escape returns to menu");
    }
    static void testDifficulty(FlappyBird game) throws Exception {
        double previousSpeed = 0, previousGravity = 0;
        for (String mode : new String[] {"easy", "normal", "hard"}) {
            call(game, "reset"); choose(game, mode);
            double speed = value(game, "pipeSpeed"), gravity = value(game, "gravity");
            check(speed > previousSpeed && gravity > previousGravity, "Ordered difficulty modes");
            previousSpeed = speed; previousGravity = gravity;
            tick(game, .1);
            check((double) get(game, "roundTime") == 0, "No ramp before starting");
            call(game, "flap");
            Object selected = get(game, "difficulty");
            choose(game, mode.equals("hard") ? "easy" : "hard");
            check(get(game, "difficulty") == selected, "Cannot change mode during play");
            set(game, "roundTime", 45.0);
            check(Math.abs(value(game, "pipeSpeed") - speed * 1.2) < .001, "Smooth speed ramp");
            check(Math.abs(value(game, "gravity") - gravity * 1.125) < .001, "Smooth gravity ramp");
            Object pipe = ((List<?>) get(game, "pipes")).get(0);
            double x = (double) get(pipe, "x"), velocity = (double) get(game, "velocity");
            tick(game, .01);
            check(Math.abs((x - (double) get(pipe, "x")) - value(game, "pipeSpeed") * .01) < .001,
                    "Ramped speed drives pipe movement");
            check(Math.abs(((double) get(game, "velocity") - velocity) - value(game, "gravity") * .01) < .001,
                    "Ramped gravity drives bird movement");
            set(game, "roundTime", 180.0);
            check(Math.abs(value(game, "pipeSpeed") - speed * 1.4) < .001, "Speed cap");
            check(Math.abs(value(game, "gravity") - gravity * 1.25) < .001, "Gravity cap");
            call(game, "gameOver"); tick(game, .1);
            check((double) get(game, "roundTime") == 180, "Ramp frozen after collision");
            call(game, "flap");
            check((double) get(game, "roundTime") == 0 && get(game, "difficulty") == selected,
                    "Restart resets ramp and preserves mode");
        }
        call(game, "gameOver"); choose(game, "normal");
        check(get(game, "state").toString().equals("READY"), "Select difficulty after game over");
    }
    public static void main(String[] args) throws Exception {
        Path testDirectory = Files.createTempDirectory("flappy-bird-test-");
        Path scoreFile = testDirectory.resolve("player/high-score.txt");
        testHighScores(testDirectory);
        SwingUtilities.invokeAndWait(() -> {
            FlappyBird game = new FlappyBird(scoreFile);
            try {
                Object sounds = get(game, "sounds");
                call(sounds, "toggle"); // Do not play test effects aloud.
                check((boolean) get(sounds, "muted"), "Mute toggle");
                testParallax(game);
                testWingAnimation(game);
                testMenu(game);
                testDifficulty(game);
                for (String name : new String[] {"jump", "score", "collision"}) {
                    try (AudioInputStream stream = AudioSystem.getAudioInputStream(new File("assets/" + name + ".wav"))) {
                        check(stream.getFormat().getSampleRate() == 44100, "WAV sample rate");
                        check(stream.getFrameLength() > 0, "WAV content");
                    }
                }
                call(game, "flap"); tick(game, 1.0 / 120);
                check((double) get(game, "birdY") < 270, "Jump");
                check((double) get(game, "velocity") > -350, "Gravity");
                Object pipe = ((List<?>) get(game, "pipes")).get(0);
                set(pipe, "x", 40.0); tick(game, 0); tick(game, 0);
                check((int) get(game, "score") == 1, "Score once per pipe");
                check(new FlappyBird.HighScoreStore(scoreFile).load() == 1, "New record saved during play");
                FlappyBird reopened = new FlappyBird(scoreFile);
                try { check((int) get(reopened, "best") == 1, "New game instance loads saved best"); }
                finally { call(get(reopened, "sounds"), "close"); }
                check((double) get(game, "scorePulse") > 0, "Score animation");
                set(game, "birdY", 560.0); tick(game, .01);
                check(get(game, "state").toString().equals("GAME_OVER"), "Ground collision");
                check((double) get(game, "hitFlash") > 0, "Collision flash");
                call(game, "flap");
                check((int) get(game, "score") == 0 && (int) get(game, "best") == 1, "Restart");
                pipe = ((List<?>) get(game, "pipes")).get(0);
                set(pipe, "x", 115.0); set(game, "birdY", 20.0); tick(game, 0);
                check(get(game, "state").toString().equals("GAME_OVER"), "Pipe collision");
                call(game, "flap"); set(game, "birdY", -1.0); tick(game, 0);
                check(get(game, "state").toString().equals("GAME_OVER"), "Ceiling collision");
                call(game, "reset"); call(game, "flap");
                pipe = ((List<?>) get(game, "pipes")).get(0);
                set(pipe, "x", 300.0);
                game.setSize(480, 640);
                BufferedImage preview = new BufferedImage(480, 640, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = preview.createGraphics();
                game.paint(graphics); graphics.dispose();
                new File("build").mkdirs();
                ImageIO.write(preview, "png", new File("build/preview.png"));
                choose(game, "menu");
                click(game, "exitButton");
                check(!((javax.swing.Timer) get(game, "timer")).isRunning(), "Exit stops timer");
                for (Clip clip : (Clip[]) get(sounds, "clips"))
                    check(clip == null || !clip.isOpen(), "Exit closes audio");
                System.out.println("PASS: parallax depth, wrapping, timing, freeze, wing animation, menus, records, difficulty, gameplay, assets, rendering");
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally {
                try { call(get(game, "sounds"), "close"); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }
        });
    }

    static void testHighScores(Path directory) throws Exception {
        Path file = directory.resolve("storage-test/high-score.txt");
        FlappyBird.HighScoreStore store = new FlappyBird.HighScoreStore(file);
        check(store.load() == 0 && store.available, "First launch has zero best");
        store.save(12);
        check(store.available && new FlappyBird.HighScoreStore(file).load() == 12, "Record survives reopening storage");
        store.save(3);
        check(store.load() == 12, "Lower scores cannot replace a record");
        for (String invalid : new String[] {"not a score", "-7", "999999999999999", ""}) {
            Files.write(file, invalid.getBytes(StandardCharsets.UTF_8));
            check(store.load() == 0 && !store.available, "Invalid save is handled");
        }
        store.save(5);
        check(store.available && store.load() == 5, "New record repairs invalid save");
        Path blocked = directory.resolve("not-a-directory");
        Files.write(blocked, new byte[] {0});
        FlappyBird.HighScoreStore unwritable = new FlappyBird.HighScoreStore(blocked.resolve("score.txt"));
        unwritable.save(10);
        check(!unwritable.available, "Save failure does not crash game");
    }
}
