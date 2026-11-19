import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/** A dependency-free Flappy Bird game. Run with: java FlappyBird */
public final class FlappyBird extends JPanel {
    private static final int WIDTH = 480, HEIGHT = 640, GROUND_Y = 580;
    private static final int BIRD_X = 115, BIRD_SIZE = 28;
    private static final int PIPE_WIDTH = 70, GAP = 170;
    private static final double JUMP = -350;
    private static final double RAMP_SECONDS = 90;
    private static final double PIPE_INTERVAL = 1.55;
    private final List<Pipe> pipes = new ArrayList<>();
    private final Random random = new Random();
    private double birdY = 270, velocity, spawnTime;
    private int score, best;
    private final HighScoreStore highScores;
    private State state = State.READY;
    private Difficulty difficulty = Difficulty.NORMAL;
    private double roundTime;
    private long lastTick;
    private final Timer timer;
    private static final double STEP = 1.0 / 120.0;
    private double accumulator, animationTime, groundOffset, tilt, flapPulse, scorePulse, hitFlash;
    private static final int PARALLAX_WIDTH = 720;
    private static final double[] PARALLAX_SPEEDS = {0.08, 0.20, 0.45};
    private static final int[] PARALLAX_Y = {80, 280, 390};
    private final double[] backgroundOffsets = new double[3];
    private final BufferedImage[] backgroundLayers = {
        loadBackgroundLayer("clouds.png", 300),
        loadBackgroundLayer("mountains.png", 300),
        loadBackgroundLayer("hills.png", 190)
    };
    private final BufferedImage[] birdFrames = loadBirdFrames();
    private static final int[] WING_CYCLE = {0, 1, 2, 1};
    private double wingPhase;
    private final BufferedImage pipeImage = loadImage("pipe.png", true);
    private final SoundBank sounds = new SoundBank();
    private final JButton playButton = new JButton("Play");
    private final JButton instructionsButton = new JButton("Instructions");
    private final JButton exitButton = new JButton("Exit");
    private final JButton backButton = new JButton("Back to menu");

    private enum State { READY, INSTRUCTIONS, PLAYING, GAME_OVER }

    private enum Difficulty {
        EASY("Easy", 150, 900), NORMAL("Normal", 175, 1050), HARD("Hard", 205, 1200);

        final String label;
        final double speed, gravity;

        Difficulty(String label, double speed, double gravity) {
            this.label = label;
            this.speed = speed;
            this.gravity = gravity;
        }
    }

    private void selectDifficulty(Difficulty selected) {
        // Keep the challenge consistent throughout a round.
        if (state == State.PLAYING || state == State.INSTRUCTIONS) return;
        difficulty = selected;
        reset();
        repaint();
    }

    private double progress() { return Math.min(roundTime / RAMP_SECONDS, 1); }
    private double pipeSpeed() { return difficulty.speed * (1 + 0.40 * progress()); }
    private double gravity() { return difficulty.gravity * (1 + 0.25 * progress()); }

    private static final class Pipe {
        double x = WIDTH;
        final int gapTop;
        boolean scored;

        Pipe(int gapTop) { this.gapTop = gapTop; }
    }

    public FlappyBird() {
        this(Paths.get(System.getProperty("user.home"), ".flappy-bird", "high-score.txt"));
    }

    // An explicit path lets tests use isolated saves without touching player data.
    FlappyBird(Path scoreFile) {
        highScores = new HighScoreStore(scoreFile);
        best = highScores.load();
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(125, 210, 235));
        setLayout(null); // The game canvas has a fixed 480 x 640 size.
        setFocusable(true);
        configureButton(playButton, 300, this::flap);
        configureButton(instructionsButton, 360, this::showInstructions);
        configureButton(exitButton, 420, this::exitGame);
        configureButton(backButton, 470, this::reset);
        updateMenuButtons();
        bind("SPACE", "flap", this::flap);
        bind("UP", "flapUp", this::flap);
        bind("ENTER", "start", this::flap);
        bind("R", "restart", () -> {
            if (state == State.PLAYING || state == State.GAME_OVER) { reset(); flap(); }
        });
        bind("ESCAPE", "menu", this::reset);
        bind("M", "mute", sounds::toggle);
        bind("1", "easy", () -> selectDifficulty(Difficulty.EASY));
        bind("2", "normal", () -> selectDifficulty(Difficulty.NORMAL));
        bind("3", "hard", () -> selectDifficulty(Difficulty.HARD));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)
                        && (state == State.PLAYING || state == State.GAME_OVER)) flap();
            }
        });
        timer = new Timer(8, event -> {
            long now = System.nanoTime();
            double dt = Math.min((now - lastTick) / 1_000_000_000.0, 0.1);
            lastTick = now;
            accumulator += dt;
            while (accumulator >= STEP) {
                if (state == State.PLAYING) updateGame(STEP);
                animate(STEP);
                accumulator -= STEP;
            }
            repaint();
        });
    }

    private void configureButton(JButton button, int y, Runnable action) {
        button.setBounds(130, y, 220, 46);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        button.setBackground(new Color(255, 220, 100));
        button.setForeground(new Color(25, 65, 75));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> action.run());
        // Space is provided by JButton; Enter activates the focused button too.
        button.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "activate");
        button.getActionMap().put("activate", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { button.doClick(); }
        });
        add(button);
    }

    private void updateMenuButtons() {
        playButton.setVisible(state == State.READY);
        instructionsButton.setVisible(state == State.READY);
        exitButton.setVisible(state == State.READY);
        backButton.setVisible(state == State.INSTRUCTIONS);
        revalidate();
        repaint();
    }

    private void showInstructions() {
        if (state != State.READY) return;
        state = State.INSTRUCTIONS;
        updateMenuButtons();
        backButton.requestFocusInWindow();
    }

    private void exitGame() {
        if (!highScores.available && best > 0) highScores.save(best);
        timer.stop();
        sounds.close();
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.dispose();
    }

    private void animate(double dt) {
        animationTime += dt;
        if (state != State.GAME_OVER) {
            double frameSeconds = state == State.PLAYING
                    ? (velocity < 0 || flapPulse > 0 ? 0.055 : 0.14) : 0.22;
            wingPhase = (wingPhase + dt / frameSeconds) % WING_CYCLE.length;
        }
        if (state != State.GAME_OVER) {
            double distance = pipeSpeed() * dt;
            groundOffset = (groundOffset + distance) % 32;
            for (int i = 0; i < backgroundOffsets.length; i++)
                backgroundOffsets[i] = (backgroundOffsets[i] + distance * PARALLAX_SPEEDS[i])
                        % (2 * PARALLAX_WIDTH);
        }
        double target = state == State.PLAYING ? Math.max(-0.4, Math.min(1.2, velocity / 550)) : 0;
        tilt += (target - tilt) * (1 - Math.exp(-12 * dt));
        flapPulse = Math.max(0, flapPulse - dt);
        scorePulse = Math.max(0, scorePulse - dt);
        hitFlash = Math.max(0, hitFlash - dt);
    }

    @Override public void addNotify() {
        super.addNotify();
        lastTick = System.nanoTime();
        timer.start();
    }

    @Override public void removeNotify() {
        timer.stop();
        sounds.close();
        super.removeNotify();
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void reset() {
        pipes.clear();
        birdY = 270;
        velocity = 0;
        score = 0;
        spawnTime = 0;
        roundTime = 0;
        accumulator = tilt = flapPulse = scorePulse = hitFlash = 0;
        wingPhase = 0;
        state = State.READY;
        updateMenuButtons();
        playButton.requestFocusInWindow();
    }

    private void flap() {
        if (state == State.INSTRUCTIONS) return;
        if (state == State.GAME_OVER) reset();
        if (state == State.READY) {
            state = State.PLAYING;
            addPipe();
            lastTick = System.nanoTime();
            updateMenuButtons();
            requestFocusInWindow();
        }
        velocity = JUMP;
        wingPhase = 0; // Every jump begins a fresh downstroke from wings-up.
        flapPulse = 0.16;
        sounds.play(0);
    }

    private void addPipe() {
        pipes.add(new Pipe(90 + random.nextInt(GROUND_Y - GAP - 180 + 1)));
    }

    private void updateGame(double dt) {
        if (state != State.PLAYING) return;
        roundTime += dt;
        velocity += gravity() * dt;
        birdY += velocity * dt;
        spawnTime += dt;
        if (spawnTime >= PIPE_INTERVAL) {
            spawnTime -= PIPE_INTERVAL;
            addPipe();
        }
        for (Pipe pipe : pipes) pipe.x -= pipeSpeed() * dt;

        Ellipse2D bird = new Ellipse2D.Double(BIRD_X, birdY, BIRD_SIZE, BIRD_SIZE);
        if (birdY <= 0 || birdY + BIRD_SIZE >= GROUND_Y) {
            birdY = Math.max(0, Math.min(birdY, GROUND_Y - BIRD_SIZE));
            gameOver();
            return;
        }
        for (Pipe pipe : pipes) {
            if (bird.intersects(pipe.x, 0, PIPE_WIDTH, pipe.gapTop)
                    || bird.intersects(pipe.x, pipe.gapTop + GAP,
                            PIPE_WIDTH, GROUND_Y - pipe.gapTop - GAP)) {
                gameOver();
                return;
            }
        }
        for (Iterator<Pipe> it = pipes.iterator(); it.hasNext();) {
            Pipe pipe = it.next();
            if (!pipe.scored && pipe.x + PIPE_WIDTH < BIRD_X) {
                pipe.scored = true;
                score++;
                if (score > best) {
                    best = score;
                    highScores.save(best);
                }
                scorePulse = 0.25;
                sounds.play(1);
            }
            if (pipe.x + PIPE_WIDTH < 0) it.remove();
        }
    }

    private void gameOver() {
        if (state == State.GAME_OVER) return;
        state = State.GAME_OVER;
        if (!highScores.available && best > 0) highScores.save(best);
        hitFlash = 0.2;
        sounds.play(2);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        drawBackground(g);
        for (Pipe pipe : pipes) {
            drawPipe(g, pipe.x, 0, pipe.gapTop, true);
            drawPipe(g, pipe.x, pipe.gapTop + GAP, GROUND_Y - pipe.gapTop - GAP, false);
        }
        g.setColor(new Color(105, 180, 75));
        g.fillRect(0, GROUND_Y, WIDTH, 12);
        g.setColor(new Color(225, 206, 145));
        g.fillRect(0, GROUND_Y + 12, WIDTH, HEIGHT - GROUND_Y - 12);

        g.setColor(new Color(190, 170, 110));
        for (int x = -32; x < WIDTH + 32; x += 32)
            g.fillRoundRect((int) (x - groundOffset), GROUND_Y + 21, 18, 4, 4, 4);
        AffineTransform old = g.getTransform();
        double bob = state == State.READY ? Math.sin(animationTime * 4) * 6 : 0;
        g.translate(BIRD_X + BIRD_SIZE / 2.0, birdY + BIRD_SIZE / 2.0 + bob);
        g.rotate(tilt);
        double squash = Math.sin(flapPulse / 0.16 * Math.PI) * 0.1;
        g.scale(1 + squash, 1 - squash);
        g.drawImage(birdFrames[birdFrameIndex()], -19, -19, 38, 38, null);
        g.setTransform(old);
        centered(g, Integer.toString(score), 60, 42 + (int) (scorePulse * 32), new Color(25, 65, 75));
        centered(g, difficulty.label + "  |  Speed +" + (int) (40 * progress())
                + "%  Gravity +" + (int) (25 * progress()) + "%", 88, 14, new Color(25, 65, 75));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(new Color(35, 70, 80));
        g.drawString("Best: " + best, 16, 28);
        if (!highScores.available)
            centered(g, "Best score could not be saved or loaded", 110, 13, new Color(120, 45, 25));
        g.drawString(sounds.muted ? "Sound off" : "Sound on", WIDTH - 100, 28);
        centered(g, "Space / Up / Click  |  R restart  |  M sound  |  Esc menu", 620, 13,
                new Color(85, 75, 50));
        if (state == State.READY || state == State.INSTRUCTIONS) {
            g.setColor(new Color(20, 40, 55, 235));
            g.fillRoundRect(40, 170, WIDTH - 80, 370, 24, 24);
            centered(g, state == State.READY ? "FLAPPY BIRD" : "HOW TO PLAY", 220, 30, Color.WHITE);
            if (state == State.READY) {
                centered(g, "1 Easy   /   2 Normal   /   3 Hard", 268, 17, Color.WHITE);
                centered(g, "Tab to choose, Enter or Space to select", 505, 14, Color.WHITE);
            } else {
                String[] lines = {
                    "Space, Up arrow or left click to flap.",
                    "Fly through the gaps to earn points.",
                    "Avoid pipes, the ceiling and the ground.",
                    "Choose difficulty with 1 / 2 / 3 in the menu.",
                    "Speed and gravity increase as you survive.",
                    "R restarts a round. M toggles sound.",
                    "Esc returns to the menu (ends the round).",
                    "Your best score is saved automatically."
                };
                for (int i = 0; i < lines.length; i++)
                    centered(g, lines[i], 255 + i * 27, 15, Color.WHITE);
            }
        } else if (state == State.GAME_OVER) {
            g.setColor(new Color(20, 40, 55, 215));
            g.fillRoundRect(40, 205, WIDTH - 80, 195, 24, 24);
            centered(g, "GAME OVER", 251, 30, Color.WHITE);
            centered(g, "Score: " + score + "    Best: " + best,
                    291, 20, Color.WHITE);
            centered(g, "1 Easy   /   2 Normal   /   3 Hard", 326, 17, Color.WHITE);
            centered(g, "Space / click to retry, Esc for menu",
                    370, 18, new Color(255, 220, 100));
        }
        if (hitFlash > 0) {
            g.setColor(new Color(255, 255, 255, (int) (hitFlash / 0.2 * 130)));
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }
        g.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(30, 165, 245),
                0, GROUND_Y, new Color(155, 235, 240)));
        g.fillRect(0, 0, WIDTH, GROUND_Y);
        for (int layer = 0; layer < backgroundLayers.length; layer++) {
            BufferedImage tile = backgroundLayers[layer];
            // Alternate normal/mirrored tiles: each join shares identical edge
            // pixels even if the source artwork is not perfectly tileable.
            for (int i = 0; i * PARALLAX_WIDTH - backgroundOffsets[layer] < WIDTH; i++) {
                double x = i * PARALLAX_WIDTH - backgroundOffsets[layer];
                if (x + PARALLAX_WIDTH <= 0) continue;
                AffineTransform transform = AffineTransform.getTranslateInstance(x, PARALLAX_Y[layer]);
                if (i % 2 != 0) {
                    transform.translate(PARALLAX_WIDTH, 0);
                    transform.scale(-1, 1);
                }
                g.drawImage(tile, transform, null);
            }
        }
    }

    private static BufferedImage loadBackgroundLayer(String name, int height) {
        BufferedImage source = loadImage(name, false);
        BufferedImage scaled = new BufferedImage(PARALLAX_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, PARALLAX_WIDTH, height, null);
        g.dispose();
        return scaled;
    }

    private void drawPipe(Graphics2D g, double x, int y, int height, boolean top) {
        AffineTransform old = g.getTransform();
        g.translate(x, top ? y + height : y);
        if (top) g.scale(1, -1);
        // Keep the lip at a fixed height; stretch only the straight shaft.
        int cut = Math.max(1, pipeImage.getHeight() / 9);
        int cap = Math.min(28, height);
        g.drawImage(pipeImage, 0, 0, PIPE_WIDTH, cap,
                0, 0, pipeImage.getWidth(), cut, null);
        g.drawImage(pipeImage, 0, cap, PIPE_WIDTH, height,
                0, cut, pipeImage.getWidth(), pipeImage.getHeight(), null);
        g.setTransform(old);
    }

    private int birdFrameIndex() { return WING_CYCLE[(int) wingPhase]; }

    private static BufferedImage[] loadBirdFrames() {
        BufferedImage sheet = loadImage("bird-flap.png", false);
        if (sheet.getWidth() != sheet.getHeight() * 3)
            throw new IllegalStateException("bird-flap.png must contain three square cells in one row");
        int size = sheet.getHeight();
        int left = size, top = size, right = -1, bottom = -1;
        // Crop all frames to the SAME union bounds so the body never shifts or
        // changes size as the wings move. Keep the source PNG untouched.
        for (int frame = 0; frame < 3; frame++) {
            boolean visible = false;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((sheet.getRGB(frame * size + x, y) >>> 24) > 20) {
                        visible = true;
                        left = Math.min(left, x); right = Math.max(right, x);
                        top = Math.min(top, y); bottom = Math.max(bottom, y);
                    }
                }
            }
            if (!visible) throw new IllegalStateException("Empty bird animation frame: " + frame);
        }
        BufferedImage[] frames = new BufferedImage[3];
        for (int frame = 0; frame < frames.length; frame++) {
            // Cache small, antialiased sprites once instead of rescaling the
            // large source sheet on every paint. 2x resolution suits HiDPI.
            frames[frame] = new BufferedImage(76, 76, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frames[frame].createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(sheet, 0, 0, 76, 76, frame * size + left, top,
                    frame * size + right + 1, bottom + 1, null);
            g.dispose();
        }
        return frames;
    }

    private static BufferedImage loadImage(String name, boolean trim) {
        URL resource = FlappyBird.class.getResource("/assets/" + name);
        try {
            if (resource == null) throw new IOException("Missing assets/" + name);
            BufferedImage image = ImageIO.read(resource);
            if (image == null) throw new IOException("Invalid image: " + name);
            if (!trim) return image;
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 20) {
                        left = Math.min(left, x); right = Math.max(right, x);
                        top = Math.min(top, y); bottom = Math.max(bottom, y);
                    }
                }
            }
            if (right < left) throw new IOException("Empty sprite: " + name);
            return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load game artwork: " + ex.getMessage(), ex);
        }
    }

    /** A small local save, replaced atomically where the filesystem supports it. */
    static final class HighScoreStore {
        private final Path file;
        boolean available = true;

        HighScoreStore(Path file) { this.file = file.toAbsolutePath(); }

        int load() {
            try {
                // A valid integer takes at most ten digits, plus whitespace.
                if (Files.size(file) > 64) throw new IOException("Invalid high-score file");
                int result = Integer.parseInt(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
                if (result < 0) throw new NumberFormatException("Negative score");
                available = true;
                return result;
            } catch (NoSuchFileException ex) {
                return 0; // First launch: a save is created on the first record.
            } catch (IOException | NumberFormatException | SecurityException ex) {
                report(ex);
                return 0;
            }
        }

        void save(int score) {
            if (score < 0) throw new IllegalArgumentException("Negative score");
            Path temporary = null;
            try {
                Files.createDirectories(file.getParent());
                // Preserve a higher record already on disk.
                int record = Math.max(score, load());
                temporary = Files.createTempFile(file.getParent(), "high-score-", ".tmp");
                Files.write(temporary, (record + "\n").getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                available = true;
            } catch (IOException | SecurityException ex) {
                report(ex);
            } finally {
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); }
                    catch (IOException | SecurityException ex) { System.err.println("Cannot remove temporary score file: " + ex.getMessage()); }
                }
            }
        }

        private void report(Exception ex) {
            available = false;
            System.err.println("High score unavailable: " + ex.getMessage());
        }
    }

    /** Clips are loaded once; playback does no file I/O on the animation loop. */
    private static final class SoundBank {
        private final Clip[] clips = new Clip[3];
        private boolean muted;
        SoundBank() {
            String[] names = {"jump", "score", "collision"};
            for (int i = 0; i < names.length; i++) {
                Clip clip = null;
                try {
                    URL url = FlappyBird.class.getResource("/assets/" + names[i] + ".wav");
                    if (url == null) throw new IOException("Missing sound: " + names[i]);
                    try (AudioInputStream stream = AudioSystem.getAudioInputStream(url)) {
                        clip = AudioSystem.getClip();
                        clip.open(stream);
                        clips[i] = clip;
                    }
                } catch (Exception ex) {
                    if (clip != null) clip.close();
                    System.err.println("Sound unavailable (" + names[i] + "): " + ex.getMessage());
                }
            }
        }
        void play(int index) {
            Clip clip = clips[index];
            if (muted || clip == null || !clip.isOpen()) return;
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
        void toggle() {
            muted = !muted;
            if (muted) for (Clip clip : clips) if (clip != null) clip.stop();
        }
        void close() { for (Clip clip : clips) if (clip != null) clip.close(); }
    }

    private void centered(Graphics2D g, String text, int y, int size, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        g.setColor(color);
        g.drawString(text, (WIDTH - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Flappy Bird");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new FlappyBird());
            frame.setResizable(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
