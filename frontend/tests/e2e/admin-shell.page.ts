import { expect, type Locator, type Page } from '@playwright/test';
import { SearchPage } from './search.page';

export class AdminShellPage {
  readonly search: SearchPage;
  readonly scheduler: SchedulerPage;

  constructor(private readonly page: Page) {
    this.search = new SearchPage(page);
    this.scheduler = new SchedulerPage(page);
  }

  async goto() {
    await this.page.goto('/');
  }

  async createAdmin(username: string, password: string) {
    await this.page.getByLabel('Username').fill(username);
    await this.page.getByLabel('Password').fill(password);
    await this.page.getByRole('button', { name: 'Create admin' }).click();
  }

  async configureSourcesFromEmptyLibrary() {
    await this.page.getByRole('button', { name: 'Configure sources' }).click();
  }

  libraryNavItem(name: RegExp) {
    return this.page.getByRole('navigation', { name: 'Libraries' }).getByRole('button', { name });
  }

  async createLibrary(name: string) {
    await this.page.getByLabel('Library name').fill(name);
    await this.page.getByRole('button', { name: 'Create' }).click();
  }

  async addSource(path: string) {
    await this.page.getByRole('textbox', { name: 'Source path' }).fill(path);
    await this.page.getByRole('button', { name: 'Add source' }).click();
  }

  async hoverSourcePathGuidance() {
    await this.page.getByRole('button', { name: 'Source path Docker guidance' }).hover();
  }

  async togglePrimaryNavigation() {
    await this.page.getByTestId('primary-nav-toggle').click();
  }

  async openPrimary(view: 'libraries') {
    await this.page.getByTestId(`primary-nav-${view}`).click();
  }

  async openSettings() {
    await this.page.getByTestId('app-shell-settings').click();
  }

  async openSettingsSection(view: 'backups' | 'plugins' | 'scheduler') {
    await this.page.getByTestId(`settings-nav-${view}`).click();
  }

  async scanLibrary(libraryId: string) {
    await this.page.getByTestId(`library-${libraryId}-scan`).click();
  }

  async openScanActivity() {
    await this.page.getByTestId('scan-activity-trigger').click();
  }

  scanActivityTrigger() {
    return this.page.getByTestId('scan-activity-trigger');
  }

  folder(name: RegExp) {
    return this.page.getByRole('button', { name });
  }

  assetTile(assetId: string) {
    return this.page.getByTestId(`asset-tile-${assetId}`);
  }

  assetImage(assetId: string) {
    return this.page.getByTestId(`asset-tile-${assetId}-image`);
  }

  async openAsset(assetId: string) {
    await this.assetTile(assetId).dblclick();
  }

  async showPhotoMetadata() {
    await this.page.getByTestId('photo-viewer-metadata-toggle').click();
  }

  async dismissPhotoMetadata() {
    await this.page.getByTestId('photo-metadata-dismiss').click();
  }

  async closePhotoViewer() {
    await this.page.getByTestId('photo-viewer-close').click();
  }

  async logout() {
    await this.page.getByTestId('app-shell-profile').click();
    await this.page.getByRole('menuitem', { name: 'Log out' }).click();
  }
}

class SchedulerPage {
  constructor(private readonly page: Page) {}

  job(jobId: string, displayName: string) {
    return new SchedulerJob(this.page.getByTestId(`scheduler-job-${jobId}`), jobId, displayName, this.page);
  }
}

class SchedulerJob {
  constructor(
    readonly row: Locator,
    private readonly jobId: string,
    private readonly displayName: string,
    private readonly page: Page
  ) {}

  get enabledToggle() {
    return this.page.getByTestId(`scheduler-job-${this.jobId}-enabled-toggle`);
  }

  async runNow() {
    await this.page.getByTestId(`scheduler-job-${this.jobId}-run-now`).click();
  }

  async toggleEnabled() {
    await this.enabledToggle.click();
  }

  async editSchedule(schedule: string, timezone: string) {
    await this.page.getByTestId(`scheduler-job-${this.jobId}-edit-schedule`).click();
    const dialog = this.page.getByRole('dialog', { name: `Edit schedule · ${this.displayName}` });
    await dialog.getByLabel('Schedule').selectOption(schedule);
    await dialog.getByLabel('Timezone').selectOption(timezone);
    await this.page.getByRole('button', { name: 'Save schedule' }).click();
  }

  async expectRanSuccessfully() {
    await expect(this.row.getByText(/^Last: (?!—$).+/)).toBeVisible();
    await expect(this.row.getByText('succeeded')).toBeVisible();
  }
}
