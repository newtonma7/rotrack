"use client";

export default function About() {
  return (
    <>
      {/* HERO SECTION */}
      <section className="min-h-screen  flex items-center justify-center"
      style={{ background: "linear-gradient(180deg, var(--color-landing-gradient-start) 0%, var(--color-landing-gradient-start) 40%, var(--color-landing-gradient-mid) 76%, var(--color-landing-gradient-end) 100%)" }}
      >
        <h1 className="text-white text-6xl font-bold">
          About Us
        </h1>
      </section>

      {/* CONTENT SECTION */}
      <section className="min-h-screen flex items-center justify-center"
      style={{ background: "var(--color-landing-gradient-end)" }}
      >
        <div className="max-w-3xl text-gray-300 text-lg text-center">
          <p>
            We are a team focused on building meaningful experiences.
            Our mission is to create products that feel simple, powerful,
            and human.
          </p>
        </div>
      </section>
    </>
  );
}
