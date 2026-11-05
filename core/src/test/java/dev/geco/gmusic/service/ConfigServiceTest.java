package dev.geco.gmusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    @Test
    void speakerListenerVolumeIsKeptBetweenZeroAndOne() {
        assertEquals(0.6, ConfigService.clampUnit(0.6), 1e-9);
        assertEquals(1.0, ConfigService.clampUnit(1.5), 1e-9);
        assertEquals(0.0, ConfigService.clampUnit(-0.2), 1e-9);
        assertEquals(1.0, ConfigService.clampUnit(Double.NaN), 1e-9);
    }

}
