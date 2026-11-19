import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/** Run after compiling FlappyBird: javac BuildWebAssets.java && java BuildWebAssets */
public class BuildWebAssets {
    public static void main(String[] args) throws Exception {
        Path out = Paths.get("web/assets"); Files.createDirectories(out);
        Method birds = FlappyBird.class.getDeclaredMethod("loadBirdFrames"); birds.setAccessible(true);
        BufferedImage[] frames = (BufferedImage[]) birds.invoke(null);
        for (int i=0;i<frames.length;i++) ImageIO.write(frames[i],"png",out.resolve("bird-"+i+".png").toFile());
        Method loader = FlappyBird.class.getDeclaredMethod("loadImage",String.class,boolean.class); loader.setAccessible(true);
        String[] names={"clouds","mountains","hills","pipe"}; int[] heights={300,300,190,630};
        for(int i=0;i<names.length;i++){
            BufferedImage original=(BufferedImage)loader.invoke(null,names[i]+".png",names[i].equals("pipe"));
            BufferedImage scaled=new BufferedImage(i==3?140:720,heights[i],BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(original,0,0,scaled.getWidth(),scaled.getHeight(),null);g.dispose();
            ImageIO.write(scaled,"png",out.resolve(names[i]+".png").toFile());
        }
        for(String name:new String[]{"jump","score","collision"})Files.copy(Paths.get("assets",name+".wav"),out.resolve(name+".wav"),StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Browser assets ready in web/assets");
    }
}
