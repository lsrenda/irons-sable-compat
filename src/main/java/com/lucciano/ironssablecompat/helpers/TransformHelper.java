package com.lucciano.ironssablecompat.helpers;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public abstract class TransformHelper {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static Vec3 AccountForSublevels(Level level, Vec3 vec) {
		// If vec is a normal destination, return it as-is
		if (!SableCompanion.INSTANCE.isInPlotGrid(level, vec)) {
			return vec;
		}
		// Get the sublevel at the location
		var sublevel = SableCompanion.INSTANCE.getContaining(level, vec);
		if (sublevel == null) {
			return UnloadedSubLevelHelper.resolveDestination(level, vec);
		}
		var global = sublevel.logicalPose().transformPosition(vec);
		LOGGER.debug("resolveDestination: used lastKnownPose, extreme={} -> world={}", vec, global);
		return global;
	}

}
