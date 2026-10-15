import * as React from 'react'
import { cn } from '../../lib/utils'

export const Label = React.forwardRef(({ className, ...props }, ref) => (
  <label
    ref={ref}
    className={cn(
      'text-sm font-semibold leading-none text-[#142534] peer-disabled:cursor-not-allowed peer-disabled:opacity-70 select-none',
      className,
    )}
    {...props}
  />
))
Label.displayName = 'Label'
