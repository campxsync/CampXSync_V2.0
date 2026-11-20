/** CampXSync Orbit X mark — matches the approved lockup */
export default function OrbitMark({ className = '', title = 'CampXSync' }) {
  return (
    <svg
      className={className}
      viewBox="0 0 100 100"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={title}
    >
      <title>{title}</title>
      {/* Orbit (behind) */}
      <ellipse
        cx="50"
        cy="50"
        rx="42"
        ry="18"
        fill="none"
        stroke="#4D8CFF"
        strokeWidth="9"
        transform="rotate(-32 50 50)"
      />
      {/* Bold geometric X */}
      <g fill="#FFFFFF">
        <rect
          x="46"
          y="8"
          width="18"
          height="98"
          rx="2"
          transform="rotate(40 50 50)"
        />
        <rect
          x="46"
          y="8"
          width="18"
          height="98"
          rx="2"
          transform="rotate(-40 50 50)"
        />
      </g>
      {/* Front orbit arc for depth */}
      <path
        d="M14 62 C28 78, 72 78, 86 62"
        fill="none"
        stroke="#4D8CFF"
        strokeWidth="9"
        strokeLinecap="round"
        transform="rotate(-32 50 50)"
      />
    </svg>
  );
}
