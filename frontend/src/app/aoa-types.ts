export type AoaRadiusMode = 'amplitude' | 'frequency' | 'age';

export interface AoaSector {
  startDegrees: number;
  endDegrees: number;
}

export interface AoaSample {
  angleHundredthDegree: number;
  amplitudeTenthDbm: number;
  frequencyKHz: number;
}

export interface AoaTile {
  receiverId: string;
  startTimeMs: number;
  durationMs: number;
  inputPulseCount: number;
  pulseCount: number;
  angleBinCount: number;
  radialBinCount: number;
  radiusMode: AoaRadiusMode;
  radialMinValue: number;
  radialMaxValue: number;
  minFrequencyKHz: number;
  maxFrequencyKHz: number;
  gap: boolean;
  counts: Uint16Array;
  samples: AoaSample[];
}

export interface AoaStreamConfiguration {
  receiverIds: string[];
  timeWindowSeconds: number;
  frequencyFromGHz: number;
  frequencyToGHz: number;
  amplitudeThresholdDbm: number;
  minAmplitudeDbm: number;
  maxAmplitudeDbm: number;
  radiusMode: AoaRadiusMode;
}
