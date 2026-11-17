import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ResultApiContext } from './resultContext';
import { ResultFiles } from './ResultFiles';

const IMAGE = { id: 'img-1', name: 'gan.png', type: 'image/png', size: null, url: 'https://res.cloudinary.com/x/gan.png', caption: '' };

function Harness({ uploadImage, deleteImage, initial = [] }) {
  const [files, setFiles] = useState(initial);
  return (
    <ResultApiContext.Provider value={{ supportsFiles: true, uploadImage, deleteImage }}>
      <ResultFiles files={files} readOnly={false} onChange={setFiles} />
    </ResultApiContext.Provider>
  );
}

describe('ResultFiles (UC-67 images on the server)', () => {
  it('uploads a picked image and shows the stored one', async () => {
    const user = userEvent.setup();
    const uploadImage = vi.fn().mockResolvedValue({ result: { files: [IMAGE] } });
    render(<Harness uploadImage={uploadImage} deleteImage={vi.fn()} />);

    const file = new File(['png'], 'gan.png', { type: 'image/png' });
    await user.upload(screen.getByLabelText('Chọn ảnh đính kèm'), file);

    expect(uploadImage).toHaveBeenCalledWith(file, '');
    expect(await screen.findByAltText('Ảnh 1')).toHaveAttribute('src', IMAGE.url);
  });

  it('refuses a PDF without calling the server', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const uploadImage = vi.fn();
    render(<Harness uploadImage={uploadImage} deleteImage={vi.fn()} />);

    await user.upload(screen.getByLabelText('Chọn ảnh đính kèm'), new File(['%PDF'], 'kq.pdf', { type: 'application/pdf' }));

    expect(screen.getByRole('alert')).toHaveTextContent('kq.pdf: chỉ nhận ảnh JPG hoặc PNG.');
    expect(uploadImage).not.toHaveBeenCalled();
  });

  it('deletes an image on the server before removing it', async () => {
    const user = userEvent.setup();
    const deleteImage = vi.fn().mockResolvedValue({ result: { files: [] } });
    render(<Harness uploadImage={vi.fn()} deleteImage={deleteImage} initial={[IMAGE]} />);

    await user.click(screen.getByRole('button', { name: 'Xóa gan.png' }));

    expect(deleteImage).toHaveBeenCalledWith('img-1');
    expect(await screen.findByText('Chưa có ảnh đính kèm.')).toBeInTheDocument();
  });
});
