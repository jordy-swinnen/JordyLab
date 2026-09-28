export interface LatestReleaseResponse {
  readonly id: string;
  readonly versionName: string;
  readonly versionCode: number;
  readonly releaseNotes: string;
  readonly sha256: string;
  readonly sizeBytes: number;
  readonly minSupportedVersionCode: number;
  readonly publishedAt: string;
  readonly updateAvailable: boolean;
  readonly updateRequired: boolean;
}
