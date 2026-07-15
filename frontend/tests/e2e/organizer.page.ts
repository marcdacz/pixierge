import { expect, type Locator, type Page } from '@playwright/test';

export class OrganizerPage {
  readonly assets: AssetGrid;
  readonly albums: OrganizerNavigation;
  readonly tags: OrganizerNavigation;

  constructor(private readonly page: Page) {
    this.assets = new AssetGrid(page);
    this.albums = new OrganizerNavigation(page.getByRole('navigation', { name: 'Albums' }));
    this.tags = new OrganizerNavigation(page.getByRole('navigation', { name: 'Tags' }));
  }

  async openPrimary(name: 'Albums' | 'Libraries' | 'Starred' | 'Tags') {
    await this.page.getByRole('navigation', { name: 'Primary' }).getByRole('button', { name }).click();
  }

  actionMenuItem(name: string | RegExp) {
    return this.page.getByRole('menuitem', { name });
  }

  assignmentPicker(kind: 'albums' | 'tags') {
    return new AssignmentPicker(this.page, kind);
  }
}

class AssetGrid {
  constructor(private readonly page: Page) {}

  tile(assetId: string) {
    return this.page.getByTestId(`asset-tile-${assetId}`);
  }

  starredBadge(assetId: string) {
    return this.tile(assetId).getByLabel('Starred');
  }

  async openActions(assetId: string) {
    const tile = this.tile(assetId);
    await tile.click({ button: 'right' });
    await expect(this.page.getByRole('menu', { name: 'Asset actions' })).toBeVisible();
    return tile;
  }

  async dragTo(assetId: string, target: Locator) {
    await this.tile(assetId).dragTo(target);
  }
}

class OrganizerNavigation {
  constructor(readonly region: Locator) {}

  item(name: string | RegExp) {
    return this.region.getByRole('button', { name });
  }
}

class AssignmentPicker {
  constructor(
    private readonly page: Page,
    private readonly kind: 'albums' | 'tags'
  ) {}

  async create(name: string) {
    await this.searchFor(name);
    const createOption = this.createOption(name);
    await expect(createOption).toBeEnabled();
    await createOption.click();
    await expect(this.page.getByRole('button', { name: `Remove ${name}` })).toBeVisible();
  }

  async searchFor(name: string) {
    await this.search.fill(name);
  }

  createOption(name: string) {
    return this.page.getByRole('option', { name: `Create ${this.singularKind} “${name}”` });
  }

  async submit() {
    await this.search.press('Enter');
    await expect(this.page.getByRole('dialog')).toBeHidden();
  }

  private get search() {
    return this.page.getByLabel(this.kind === 'albums' ? 'Search add to albums' : 'Search add tags');
  }

  private get singularKind() {
    return this.kind === 'albums' ? 'album' : 'tag';
  }
}
