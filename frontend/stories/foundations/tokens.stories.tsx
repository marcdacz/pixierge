import type { Meta, StoryObj } from '@storybook/react-vite';
import { TokenPreview } from '../../src/design-system/foundations/token-preview';

const meta = {
  title: 'Foundations/Tokens',
  component: TokenPreview,
  parameters: {
    layout: 'fullscreen'
  }
} satisfies Meta<typeof TokenPreview>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Dark: Story = {};

export const Light: Story = {
  render: () => (
    <div data-theme="light">
      <TokenPreview />
    </div>
  )
};
