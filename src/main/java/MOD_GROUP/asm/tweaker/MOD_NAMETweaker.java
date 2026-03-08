package MOD_GROUP.asm.tweaker;

import java.io.File;
import java.util.List;

import MOD_GROUP.asm.MOD_NAMEClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class MOD_NAMETweaker implements ITweaker {

	@Override
	public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {

	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader classLoader) {
		classLoader.registerTransformer(MOD_NAMEClassTransformer.class.getName());
	}

	@Override
	public String getLaunchTarget() {
		throw new RuntimeException("Invalid for use as a primary tweaker");
	}

	@Override
	public String[] getLaunchArguments() {
		return new String[0];
	}

}
