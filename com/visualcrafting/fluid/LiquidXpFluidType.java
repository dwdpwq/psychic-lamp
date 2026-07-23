package com.visualcrafting.fluid;

import net.neoforged.neoforge.fluids.FluidType;

public class LiquidXpFluidType extends FluidType {
    public LiquidXpFluidType() {
        super(FluidType.Properties.create()
                .density(1000)
                .viscosity(1000)
                .canSwim(false)
                .canDrown(false)
                .supportsBoating(false));
    }
}
