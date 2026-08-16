import { AdminProductEditorPage } from "@/features/admin/product-editor";

export default async function EditAdminProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const { productId } = await params;
  return <AdminProductEditorPage productId={productId} />;
}
