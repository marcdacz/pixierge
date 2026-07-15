import { type Page } from '@playwright/test';

export class SearchPage {
  constructor(private readonly page: Page) {}

  get input() {
    return this.page.getByTestId('structured-search-input');
  }

  suggestion(value: string) {
    return this.page.getByTestId(`structured-search-suggestion-${searchTestIdPart(value)}`);
  }

  removePill(field: string, value: string, options: { negated?: boolean } = {}) {
    const negated = options.negated ? 'not-' : '';
    return this.page.getByTestId(`structured-search-pill-remove-${negated}${field}-${searchTestIdPart(value)}`);
  }

  async typeToken(value: string) {
    await this.input.fill(value);
  }

  async commitDraft() {
    await this.input.press(' ');
  }
}

function searchTestIdPart(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'empty';
}
