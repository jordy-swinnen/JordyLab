import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BrandMarkComponent } from '@jordylab-fe/shared/brand';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMarkComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
