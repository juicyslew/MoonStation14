package com.juicyslew.moonstation14.ms14.singularity.pa;

/** Common swept-contact energy admission for PA energy sources. */
public interface ParticleAcceleratorEnergyReceiver {
    double particleContactRadius();
    double particleEnergy();
    void receiveParticleEnergy(ParticleAcceleratorStrengthProfile strength);
}
