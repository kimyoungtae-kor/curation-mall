import { apiFetch, apiMutation, refreshCsrfToken } from "@/lib/api/client";
import type {
  AuthResult,
  AuthSnapshot,
  Cart,
  WishlistPage,
} from "./types";

type DataResponse<T> = { data: T };

export type LoginInput = { email: string; password: string };
export type SignupInput = LoginInput & {
  name: string;
  phone: string;
  requiredTermsAccepted: boolean;
};

export async function getAuthSnapshot(signal?: AbortSignal) {
  const response = await apiFetch<DataResponse<AuthSnapshot>>("/auth/me", { signal });
  return response.data;
}

async function completeAuth(path: string, body: LoginInput | SignupInput) {
  const response = await apiMutation<DataResponse<AuthResult>>(path, {
    method: "POST",
    body,
  });
  await refreshCsrfToken();
  return response.data;
}

export function login(input: LoginInput) {
  return completeAuth("/auth/login", input);
}

export function signup(input: SignupInput) {
  return completeAuth("/auth/signup", input);
}

export async function logout() {
  await apiMutation<void>("/auth/logout", { method: "POST" });
  await refreshCsrfToken();
}

export async function getCart(signal?: AbortSignal) {
  const response = await apiFetch<DataResponse<Cart>>("/cart", { signal });
  return response.data;
}

export async function addCartItem(variantId: string, quantity: number) {
  const response = await apiMutation<DataResponse<Cart>>("/cart/items", {
    method: "POST",
    body: { variantId, quantity },
  });
  return response.data;
}

export async function updateCartItem(itemId: string, quantity: number) {
  const response = await apiMutation<DataResponse<Cart>>(
    `/cart/items/${encodeURIComponent(itemId)}`,
    { method: "PATCH", body: { quantity } },
  );
  return response.data;
}

export async function removeCartItem(itemId: string) {
  const response = await apiMutation<DataResponse<Cart>>(
    `/cart/items/${encodeURIComponent(itemId)}`,
    { method: "DELETE" },
  );
  return response.data;
}

export function getWishlist(signal?: AbortSignal) {
  return apiFetch<WishlistPage>("/wishlist?page=0&size=100", { signal });
}

export async function addWishlistItem(productId: string) {
  const response = await apiMutation<
    DataResponse<{ productId: string; wishlisted: true; wishlistCount: number }>
  >(`/wishlist/${encodeURIComponent(productId)}`, { method: "POST" });
  return response.data;
}

export function removeWishlistItem(productId: string) {
  return apiMutation<void>(`/wishlist/${encodeURIComponent(productId)}`, {
    method: "DELETE",
  });
}
