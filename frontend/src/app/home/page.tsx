"use client";

import { useAuth } from "../../context/AuthProvider";
import { useState } from "react";


export default function LandingPage() {
  const { user, loading } = useAuth();
  return (
    <div className="min-h-screen w-full bg-landing-gradient-end">
      {/* Hero Section with Gradient Background */}
      <section className="w-full min-h-[60vh] flex flex-col items-center justify-center px-8 py-24 bg-hero-gradient">
        <div className="max-w-4xl mx-auto flex flex-col items-center">
          {/* Eyebrow Text */}
          <p className="text-sm mb-6 text-center font-medium text-landing-eyebrow">
            Eyebrow text to label this content
          </p>
          
          {/* Main Headline */}
          <h1 className="text-6xl font-bold text-white text-center mb-10 leading-tight">
            A bold headline that delivers
          </h1>
          
          {/* Call to Action Buttons */}
          <div className="flex gap-4">
            <button className="bg-black text-white px-8 py-3 rounded-md font-medium hover:bg-gray-900 transition-colors">
              Call to action
            </button>
            <button className="text-white px-8 py-3 rounded-md font-medium hover:opacity-90 transition-opacity bg-landing-gradient-mid">
              Secondary
            </button>
          </div>
        </div>
      </section>

      {/* Content Section with Dark Background */}
      <section className="w-full px-8 py-16 bg-landing-gradient-end">
        <div className="max-w-4xl mx-auto">
          {/* Eyebrow Text */}
          <p className="text-sm mb-4 font-medium text-landing-eyebrow">
            Eyebrow text to label this content
          </p>
          
          {/* Section Headline */}
          <h2 className="text-4xl font-bold text-white mb-6">
            A headline for some text
          </h2>
          
          {/* Content Paragraph */}
          <p className="text-white text-lg leading-relaxed">
            On the one hand, all you need to do is say what you mean, in your words, in your voice. On the other, there are so many rules to consider! Are you thinking of keywords you should rank for? Are you including links in your text to additional information? Do those links refer back to your own website, which helps boost your SEO? Is what you&apos;ve written easy to scan? There&apos;s a theory that people read in an F-shape pattern, and that this should influence how you structure content on your website. Lots of ins and outs—it&apos;s no wonder writers rule the world.
          </p>
        </div>
      </section>
    </div>
  );
}