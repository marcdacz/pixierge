import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { LibraryNorthStar } from '../../../stories/screens/library-north-star.stories';

afterEach(() => cleanup());

describe('LibraryNorthStar story', () => {
  it('renders the dark north-star screen without runtime reference errors', () => {
    render(<LibraryNorthStar />);

    expect(screen.getByRole('heading', { name: 'All folders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Search filters' })).toBeInTheDocument();
    expect(screen.getByRole('slider', { name: 'Zoom media grid' })).toBeInTheDocument();
  });

  it('renders the light north-star screen without runtime reference errors', () => {
    render(<LibraryNorthStar theme="light" />);

    expect(screen.getByText('12,842 items')).toBeInTheDocument();
  });
});
