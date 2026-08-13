import type { ReactElement } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MediaBrowserPatterns, ShellNavigation } from '../../../stories/components/patterns.stories';

afterEach(() => cleanup());

type RenderableStory = {
  render?: (...args: never[]) => ReactElement;
};

function renderStory(story: RenderableStory) {
  if (!story.render) {
    throw new Error('Story is missing a render function');
  }
  render(story.render());
}

describe('pattern stories', () => {
  it('renders the shell navigation story without runtime reference errors', () => {
    renderStory(ShellNavigation as RenderableStory);

    expect(screen.getByRole('textbox', { name: 'Search library' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Grid view' })).toBeInTheDocument();
  });

  it('renders the media browser story without runtime reference errors', () => {
    renderStory(MediaBrowserPatterns as RenderableStory);

    expect(screen.getByRole('heading', { level: 1, name: 'May 24, 2025' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Selected clip' })).toBeInTheDocument();
  });
});
