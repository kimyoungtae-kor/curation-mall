"use client";

import { useEffect, useState } from "react";
import { readCheckoutHandoff } from "./storage";
import type { CheckoutHandoff } from "./types";

type CheckoutHandoffState = {
  handoff: CheckoutHandoff | null;
  loading: boolean;
};

const INITIAL_STATE: CheckoutHandoffState = {
  handoff: null,
  loading: true,
};

export function useCheckoutHandoff(orderNumber: string): CheckoutHandoffState {
  const [state, setState] = useState<CheckoutHandoffState>(INITIAL_STATE);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setState({
        handoff: readCheckoutHandoff(orderNumber),
        loading: false,
      });
    }, 0);

    return () => window.clearTimeout(timer);
  }, [orderNumber]);

  return state;
}
