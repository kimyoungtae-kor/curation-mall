import type { Metadata } from "next";
import { HomeExperience } from "@/features/home/home-experience";

export const metadata: Metadata = {
  title: "반려생활 큐레이션",
  description: "반려동물의 행동과 사람의 공간을 함께 생각한 라이프스타일 큐레이션",
};

export default function Home() {
  return <HomeExperience />;
}
